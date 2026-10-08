package com.whitefog.darkness.light;

import com.whitefog.WhiteFog;
import com.whitefog.darkness.light.LightFuelComponent.LightFuel;
import com.whitefog.darkness.light.PortableLightPolicy.Kind;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Серверно-авторитетный сервис переносного света и топлива предметов-источников (тикет поверх 1.7).
 *
 * <p><b>Переносной свет (левая рука).</b> Свет даёт только предмет в ЛЕВОЙ руке, чей item-компонент
 * {@code white_fog:light_fuel} имеет {@code remainingTicks > 0} И {@code lit = true}; отсутствие
 * компонента/0/негатив/{@code lit=false} = света нет, заряженный {@code count > 1} = повреждён
 * (света нет, операции запрещены). Эмиссия совпадает с block-эмиссией факела 14 и факела душ 10.</p>
 *
 * <p>В exposure-сервисе значение block light клетки глаза поднимается адаптером
 * {@link PortableLightPolicy#effectiveBlockLight(int, int)} до {@code max(vanilla, emission)} ДО вызова
 * неизменной формулы {@link com.whitefog.darkness.LightExposurePolicy}. Это НЕ радиус и НЕ сложение;
 * штатный world block light, dynamic lights и формула тьмы не меняются.</p>
 *
 * <p><b>Расход топлива в инвентаре.</b> Из единственного {@code END_SERVER_TICK} вызывается
 * {@link #tickInventories(MinecraftServer)}: у каждого online-игрока тикается основной инвентарь
 * ({@link Inventory#getNonEquipmentItems()}) И левая рука ровно по одному разу (count=1). Горящий
 * предмет с запасом теряет 1 тик за серверный тик, {@code 1 -> 0} сразу тушит ({@code lit=false});
 * погашенный не тратит топливо. Item-entity и чужие инвентари не тикаются. Второй
 * {@code END_SERVER_TICK} не регистрируется, серверный source-block tick и attachment не меняются.</p>
 */
public final class PortableLightService {
	private static boolean registered = false;

	private PortableLightService() {
	}

	/** Регистрация сервиса (логирование). Идемпотентна. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WhiteFog.LOGGER.info(
				"White Fog: portable offhand light adapter registered (stage 1.7, offhand torch/soul torch "
						+ "+ inventory fuel tick)");
	}

	/** Вид переносного света для стека или {@code null}. */
	public static Kind kindForStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id == null) {
			return null;
		}
		return PortableLightPolicy.kindForItemId(id.toString());
	}

	/**
	 * Эмиссия переносного света от лежащего в руке стека (0..15). Учитывает {@code lit}, компонент,
	 * порчу ({@code count > 1}) и clamp по ёмкости. 0 = света нет.
	 */
	public static int emissionForStack(ItemStack stack) {
		Kind kind = kindForStack(stack);
		if (kind == null) {
			return 0;
		}
		LightFuel fuel = LightFuelComponent.read(stack);
		int remaining = PortableLightPolicy.normalize(fuel.remainingTicks(), kind.capacity);
		boolean corrupt = LightFuelComponent.isInvalidChargedStack(stack, fuel.remainingTicks());
		return PortableLightPolicy.emission(kind, true, corrupt, fuel.lit(), remaining);
	}

	/** Эмиссия переносного света текущей левой руки игрока (0 = нет света). */
	public static int portableEmission(ServerPlayer player) {
		return emissionForStack(player.getOffhandItem());
	}

	/** Эффективный block light для exposure: {@code max(vanilla, portable emission)}. */
	public static int effectiveBlockLight(ServerPlayer player, int vanillaBlockLight) {
		return PortableLightPolicy.effectiveBlockLight(vanillaBlockLight, portableEmission(player));
	}

	// ------------------------------------------------------------------
	// Расход топлива предметов в инвентаре (серверный тик)
	// ------------------------------------------------------------------

	/**
	 * Один серверный тик расхода топлива предметов-источников во всех инвентарях online-игроков:
	 * основной инвентарь + левая рука (ровно по одному разу). Защищено try/catch на игрока.
	 */
	public static void tickInventories(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer player : players) {
			try {
				tickPlayerInventory(player);
			} catch (RuntimeException e) {
				WhiteFog.LOGGER.error("White Fog: portable light inventory tick failed for player {}",
						player.getStringUUID(), e);
			}
		}
	}

	/**
	 * Основной инвентарь ({@link Inventory#getNonEquipmentItems()}) + левая рука. Левая рука НЕ входит
	 * в {@code getNonEquipmentItems()}, поэтому двойного тика одного слота нет.
	 */
	private static void tickPlayerInventory(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (ItemStack stack : inventory.getNonEquipmentItems()) {
			tickStack(stack);
		}
		tickStack(player.getOffhandItem());
	}

	/**
	 * Один тик одного стека: тикается только управляемый предмет-источник, ГОРЯЩИЙ
	 * ({@code lit}) и с запасом, с целым стеком ({@code count=1}). {@code 1 -> 0} сразу тушит.
	 */
	private static void tickStack(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !LightSourceBlocks.isManagedItem(stack)) {
			return;
		}
		LightFuel fuel = LightFuelComponent.read(stack);
		if (!fuel.burning() || LightFuelComponent.isInvalidChargedStack(stack, fuel.remainingTicks())) {
			return;
		}
		int next = fuel.remainingTicks() - 1;
		if (next <= 0) {
			LightFuelComponent.writeFuel(stack, 0, false);
		} else {
			LightFuelComponent.writeFuel(stack, next, true);
		}
	}
}

package com.whitefog.crafting;

import com.whitefog.WhiteFog;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.BlockEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Полный запрет ванильного крафта (этап 1.2).
 *
 * <p>Механика реализована исключительно серверно-авторитетно и без собственной замены крафта:</p>
 * <ol>
 *     <li><b>Инвентарная сетка 2x2 ({@code minecraft:inventory})</b> — недоступна: любые клики по
 *         слотам входа и результата меню {@link InventoryMenu} отменяются на сервере, результата нет.
 *         При попытке игрок получает сообщение {@link #MESSAGE_CANNOT_CRAFT}.</li>
 *     <li><b>Верстак 3x3 ({@code minecraft:crafting_table})</b> — обычный блок-верстак нельзя открыть
 *         ({@link BlockEvents#USE_WITHOUT_ITEM}), сам блок не разрушается. Дополнительно любые клики
 *         по сетке/результату меню {@link CraftingMenu} отменяются — на случай, если меню открыто
 *         другим путём (командой/модом/спектатором).</li>
 *     <li><b>Рецепты</b> — все рецепты типа {@link RecipeType#CRAFTING} удаляются из
 *         {@link RecipeManager} (включая рецепт самого блока {@code minecraft:crafting_table}),
 *         поэтому результат крафта не находится и книга рецептов пуста.</li>
 * </ol>
 *
 * <p>Все служебные (временные) слоты сетки крафта не хранятся: {@code InventoryMenu}/{@code CraftingMenu}
 * являются transient-контейнерами, а ванильный {@code AbstractContainerMenu#removed(Player)} возвращает
 * их содержимое игроку (или выбрасывает при полном инвентаре) при закрытии меню. Мод не удаляет и не
 * дублирует входные предметы.</p>
 *
 * <p><b>Источники-референсы (адаптировано, не скопировано):</b></p>
 * <ul>
 *     <li>{@code Zergatul/cheatutils} —
 *         {@code common/java/com/zergatul/cheatutils/mixins/common/MixinAbstractContainerMenu.java}:
 *         паттерн {@code @Inject(method = "clicked", at = @At("HEAD"))} для перехвата кликов меню в MC 26.2.</li>
 *     <li>{@code gnembon/fabric-carpet} —
 *         {@code src/main/java/carpet/mixins/AbstractCraftingMenu_scarpetMixin.java}:
 *         перехват {@code AbstractCraftingMenu#handlePlacement} с возвратом
 *         {@code RecipeBookMenu.PostPlaceAction.NOTHING} для запрета выкладывания рецепта.</li>
 *     <li>{@code FabricMC/fabric-api} —
 *         {@code fabric-recipe-api-v1/.../mixin/recipe/RecipeManagerMixin.java} и
 *         {@code .../RecipeManagerAccessor.java}: подтверждение внутреннего поля {@code RecipeMap recipes}
 *         и точки {@code apply(RecipeMap, ResourceManager, ProfilerFiller)}.</li>
 * </ul>
 */
public final class CraftingLock {
	/** Сообщение, выдаваемое игроку при любой попытке ванильного крафта. */
	public static final Component MESSAGE_CANNOT_CRAFT = Component.literal("Крафтить на бегу нельзя");

	/** Ключ отладочной строки self-теста (используется bounded smoke-проверкой). */
	public static final String SELFTEST_MARKER = "WHITEFOG_CRAFTING_SELFTEST";

	/** Защита от повторной регистрации обработчиков. */
	private static boolean registered = false;

	private CraftingLock() {
	}

	/** Регистрирует серверные хуки запрета крафта. Повторный вызов безопасен. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		// Блокируем открытие обычного верстака (блок не разрушаем).
		BlockEvents.USE_WITHOUT_ITEM.register(CraftingLock::onUseWithoutItem);

		// Self-тест: проверяем, что рецепты крафта действительно удалены (одна строка в лог).
		ServerLifecycleEvents.SERVER_STARTED.register(server -> logRecipeSelfTest(server, "start"));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
				(server, resourceManager, success) -> logRecipeSelfTest(server, success ? "reload" : "reload-failed"));

		WhiteFog.LOGGER.info("White Fog: crafting lock registered (inventory 2x2 + crafting table 3x3)");
	}

	// ------------------------------------------------------------------
	// Защита серверных путей меню крафта
	// ------------------------------------------------------------------

	/**
	 * Является ли меню одним из ванильных меню крафта, которые мы запрещаем.
	 *
	 * <p>В MC 26.2 у {@link InventoryMenu} вообще нет зарегистрированного {@link MenuType}
	 * (конструктор передаёт {@code null}), поэтому «тип» инвентарного меню определяется по классу,
	 * а верстака — дополнительно по {@link MenuType#CRAFTING}.</p>
	 */
	public static boolean isCraftingMenu(AbstractContainerMenu menu) {
		if (menu instanceof InventoryMenu) {
			return true;
		}
		if (menu instanceof CraftingMenu) {
			return menu.getType() == MenuType.CRAFTING;
		}
		return false;
	}

	/**
	 * Относится ли слот к сетке входа или слоту результата меню крафта.
	 * Не использует жёстко прописанные индексы — берёт их из {@link AbstractCraftingMenu}.
	 */
	public static boolean isCraftingSlot(AbstractContainerMenu menu, int slotId) {
		if (!isCraftingMenu(menu)) {
			return false;
		}
		if (slotId < 0 || !menu.isValidSlotIndex(slotId)) {
			return false;
		}
		AbstractCraftingMenu craftingMenu = (AbstractCraftingMenu) menu;
		Slot slot = menu.getSlot(slotId);
		if (slot == craftingMenu.getResultSlot()) {
			return true;
		}
		return craftingMenu.getInputGridSlots().contains(slot);
	}

	/**
	 * Решение о блокировке конкретного клика. Проверяет тип меню, принадлежность слота сетке/результату,
	 * владельца текущего меню и (для верстака) дистанцию до блока.
	 */
	public static boolean shouldBlockClick(AbstractContainerMenu menu, int slotId, Player player) {
		if (menu == null || player == null) {
			return false;
		}
		if (!isCraftingSlot(menu, slotId)) {
			return false;
		}
		// Владелец/активное меню: клик обязан относиться к текущему открытому меню игрока.
		if (player.containerMenu != menu) {
			return false;
		}
		// Дистанция (для верстака привязанного к блоку). Невалидное меню всё равно блокируется,
		// чтобы не дать обойти проверку; сервер закроет его на ближайшем тике.
		if (menu instanceof CraftingMenu craftingMenu && !craftingMenu.stillValid(player)) {
			WhiteFog.LOGGER.debug("White Fog: crafting click in invalid/out-of-range crafting menu (player={})",
					player.getName().getString());
		}
		return true;
	}

	/** Побочные эффекты отказа: сообщение игроку (только сервер) и принудительная ресинхронизация меню. */
	public static void onBlockedClick(AbstractContainerMenu menu, int slotId, Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(MESSAGE_CANNOT_CRAFT, true);
			// Отменяем предсказание клиента и гарантируем отсутствие рассинхрона/дюпа.
			menu.sendAllDataToRemote();
		}
	}

	// ------------------------------------------------------------------
	// Удаление ванильных рецептов крафта
	// ------------------------------------------------------------------

	/**
	 * Возвращает новый {@link RecipeMap} без рецептов {@link RecipeType#CRAFTING}
	 * (включая {@code minecraft:crafting_table}). Вызывается из {@code RecipeManager#apply}.
	 */
	public static RecipeMap filterOutCraftingRecipes(RecipeMap original) {
		if (original == null) {
			return null;
		}
		List<RecipeHolder<?>> kept = new ArrayList<>();
		int removed = 0;
		for (RecipeHolder<?> holder : original.values()) {
			if (holder.value().getType() == RecipeType.CRAFTING) {
				removed++;
			} else {
				kept.add(holder);
			}
		}
		if (removed == 0) {
			return original;
		}
		WhiteFog.LOGGER.info("White Fog: removed {} vanilla crafting recipe(s) from recipe manager", removed);
		return RecipeMap.create(kept);
	}

	/** Количество рецептов крафта в наборе. */
	public static int countCraftingRecipes(Iterable<RecipeHolder<?>> recipes) {
		int count = 0;
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value().getType() == RecipeType.CRAFTING) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Пишет в лог одну строку self-теста: сколько рецептов крафта осталось и есть ли рецепт верстака.
	 * Формат машиночитаем: {@code WHITEFOG_CRAFTING_SELFTEST phase=... craftingRecipes=... craftingTableRecipe=... status=SUCCESS|FAILURE}.
	 */
	public static void logRecipeSelfTest(MinecraftServer server, String phase) {
		try {
			RecipeManager manager = server.getRecipeManager();
			Collection<RecipeHolder<?>> recipes = manager.getRecipes();
			int crafting = countCraftingRecipes(recipes);

			ResourceKey<Recipe<?>> tableKey = ResourceKey.create(
					Registries.RECIPE, Identifier.withDefaultNamespace("crafting_table"));
			boolean tableRecipePresent = manager.byKey(tableKey).isPresent();

			String status = (crafting == 0 && !tableRecipePresent) ? "SUCCESS" : "FAILURE";
			WhiteFog.LOGGER.info("{} phase={} craftingRecipes={} craftingTableRecipe={} status={}",
					SELFTEST_MARKER, phase, crafting, tableRecipePresent, status);
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("{} phase={} status=FAILURE (self-test crashed)",
					SELFTEST_MARKER, phase, e);
		}
	}

	// ------------------------------------------------------------------
	// Запрет открытия верстака (без разрушения блока)
	// ------------------------------------------------------------------

	/**
	 * Перехват использования блока с пустой рукой. Для {@code minecraft:crafting_table} возвращаем
	 * {@link InteractionResult#FAIL}: ванильный {@code useWithoutItem} (открытие 3x3-меню) не выполняется,
	 * при этом блок не удаляется. Возврат {@code null} — «пропустить дальше» для всех прочих блоков.
	 */
	private static InteractionResult onUseWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (state.getBlock() != Blocks.CRAFTING_TABLE) {
			return null;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(MESSAGE_CANNOT_CRAFT, true);
		}
		return InteractionResult.FAIL;
	}
}

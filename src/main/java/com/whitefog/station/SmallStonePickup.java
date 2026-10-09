package com.whitefog.station;

import com.whitefog.WhiteFogConfig;
import com.whitefog.WhiteFog;
import com.whitefog.content.WhiteFogContent;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Подбор камушка обычным ПКМ (этап 1.4).
 *
 * <p>Серверно-авторитетно: удаляем блок без block loot и выдаём ровно один предмет
 * (в инвентарь, при полном — pending-drop с задержкой 10 тиков). Cooldown
 * {@value com.whitefog.WhiteFogConfig#SMALL_STONE_PICKUP_COOLDOWN_TICKS} тиков на игрока
 * страхует от двойного клика; повторный клик по уже удалённому блоку видит air и не даёт
 * второго предмета (однократность на серверном потоке). Предмет в руке не расходуется.</p>
 */
public final class SmallStonePickup {

	/** Последний тик подбора на игрока (cooldown). */
	private static final Map<UUID, Long> LAST_PICKUP_TICK = new HashMap<>();

	private SmallStonePickup() {
	}

	/** Подбирает камушек по позиции. Возвращает {@code true}, если предмет был выдан. */
	public static boolean pickup(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() != WhiteFogContent.SMALL_STONE) {
			return false;
		}
		long now = level.getLevelData().getGameTime();
		Long last = LAST_PICKUP_TICK.get(player.getUUID());
		if (last != null && now - last < WhiteFogConfig.SMALL_STONE_PICKUP_COOLDOWN_TICKS) {
			return false;
		}
		LAST_PICKUP_TICK.put(player.getUUID(), now);
		// Interaction-pickup: block loot подавлен, ровно один предмет.
		boolean removed = level.removeBlock(pos, false);
		if (!removed) {
			return false;
		}
		boolean dropped = StationDropHelper.giveOne(player, level, pos,
				new ItemStack(WhiteFogContent.SMALL_STONE_ITEM));
		WhiteFog.LOGGER.info("WHITEFOG_SMALL_STONE_PICKUP player={} dimension={} pos={} pendingDrop={}",
				player.getStringUUID(), level.dimension().identifier(), pos, dropped);
		if (dropped) {
			player.sendSystemMessage(Component.literal(WhiteFogConfig.MESSAGE_INVENTORY_FULL), true);
		}
		return true;
	}

	/** Очистка cooldown при disconnect/respawn/смене измерения. */
	public static void clear(ServerPlayer player) {
		if (player != null) {
			LAST_PICKUP_TICK.remove(player.getUUID());
		}
	}
}

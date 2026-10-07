package com.whitefog.station;

import com.whitefog.WhiteFogConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Единая выдача «ровно одного предмета» при interaction-pickup (этап 1.4).
 *
 * <p>Порядок ровно как в ТЗ: сначала попытка положить в инвентарь
 * ({@link net.minecraft.world.entity.player.Inventory#add(ItemStack)} мутирует стек, оставляя
 * остаток), а остаток выбрасывается обычным {@link ItemEntity} в центре бывшего блока с
 * задержкой подбора {@value com.whitefog.WhiteFogConfig#PENDING_DROP_PICKUP_DELAY_TICKS} тиков.
 * Итого — ровно один предмет, без потери и без дубля.</p>
 */
public final class StationDropHelper {
	private StationDropHelper() {
	}

	/**
	 * Выдаёт один предмет игроку; при полном инвентаре остаток падает на землю.
	 *
	 * @return {@code true}, если предмет НЕ влез целиком и часть выпала на землю.
	 */
	public static boolean giveOne(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
		ItemStack leftover = stack.copy();
		player.getInventory().add(leftover);
		if (!leftover.isEmpty()) {
			dropAt(level, pos, leftover);
			return true;
		}
		return false;
	}

	/** Кладёт стек обычным ItemEntity в центре блока с задержкой подбора 10 тиков. */
	public static void dropAt(ServerLevel level, BlockPos pos, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity entity = new ItemEntity(level,
				pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
		entity.setPickUpDelay(WhiteFogConfig.PENDING_DROP_PICKUP_DELAY_TICKS);
		level.addFreshEntity(entity);
	}
}

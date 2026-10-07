package com.whitefog.breaking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Безопасное снятие станции (этап 1.3).
 *
 * <p>Выдаёт ровно один предмет станции и не выбрасывает содержимое. Содержимое не удаляется:
 * наполненные станции/ёмкости отклоняются на этапе валидации {@code BreakTimerService}
 * (см. {@link BlockBreakRules#isFilled}) и ещё раз перепроверяются здесь как последний
 * defensive guard ПЕРЕД {@code removeBlockEntity} — содержимое не может быть потеряно.</p>
 *
 * <p>Порядок: перечитать состояние и проверить наполненность, получить предмет-клон блока до
 * удаления, удалить block entity (чтобы исключить любой ванильный выброс содержимого), удалить
 * блок без дропа, выдать ровно один предмет. Приём {@code removeBlockEntity} + {@code removeBlock}
 * повторяет безопасное снятие станции без дублей из {@code Tschipp/CarryOn} ({@code 26.2}) —
 * {@code Common/.../carry/PickupHandler.java} (адаптировано, не скопировано).</p>
 */
public final class StationRemoval {
	private StationRemoval() {
	}

	/** Снимает пустую станцию и выдаёт ровно один предмет блока. Возвращает {@code true}, если сняла. */
	public static boolean commit(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		// Последний defensive guard: наполненную станцию НЕ снимаем (содержимое не должно исчезнуть).
		if (BlockBreakRules.isFilled(level, pos, state)) {
			return false;
		}
		ItemStack stationItem = state.getCloneItemStack(level, pos, false);
		if (stationItem.isEmpty()) {
			// Резерв: ванильный предмет блока (например, если clone по какой-то причине пуст).
			stationItem = new ItemStack(state.getBlock().asItem());
		}
		// Удаляем block entity ДО блока: содержимое станции не выбрасывается и не дублируется.
		level.removeBlockEntity(pos);
		// recursionLeft = 512 — как в ванильных вызовах destroyBlock.
		boolean removed = level.destroyBlock(pos, false, player, 512);
		if (removed && !stationItem.isEmpty()) {
			Block.popResource(level, pos, stationItem.copy());
		}
		return removed;
	}
}

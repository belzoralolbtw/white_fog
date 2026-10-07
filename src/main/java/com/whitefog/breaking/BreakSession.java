package com.whitefog.breaking;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Активная серверная сессия разрушения блока (этап 1.3).
 *
 * <p>Хранит ровно то, что требует ROADMAP_STEPS.md: позицию, UUID игрока, измерение,
 * инструмент (включая data-компоненты — хранится копия {@link ItemStack}), стартовый тик,
 * требуемую длительность, исходное состояние блока и категорию. Клиентский progress bar
 * здесь не участвует: завершение определяет только серверный тик.</p>
 *
 * @param playerId     UUID игрока
 * @param dimension    измерение на момент старта ({@code level.dimension()})
 * @param pos          позиция блока (immutable)
 * @param tool         копия инструмента (сравнение через {@code ItemStack.isSameItemSameComponents})
 * @param startState   состояние блока на момент старта
 * @param category     категория блока
 * @param toolKind     категория инструмента
 * @param startTick    игровой тик старта ({@code level.getLevelData().getGameTime()})
 * @param requiredTicks требуемая длительность в тиках
 */
public record BreakSession(
		UUID playerId,
		ResourceKey<Level> dimension,
		BlockPos pos,
		ItemStack tool,
		BlockState startState,
		BlockBreakRules.Category category,
		BlockBreakRules.ToolKind toolKind,
		long startTick,
		int requiredTicks
) {
	/** Дозрел ли серверный таймер к моменту {@code now}. */
	public boolean ready(long now) {
		return now - startTick >= requiredTicks;
	}
}

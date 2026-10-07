package com.whitefog.tests.breaktimer;

/**
 * Активная сессия разрушения: серверный «таймер» по ROADMAP_STEPS.md этап 1.3
 * (позиция + UUID + инструмент + стартовый тик + требуемая длительность).
 */
public record BreakSession(
		String playerUuid,
		BlockKey key,
		ToolStack tool,
		ToolKind toolKind,
		BlockKind startKind,
		long startTick,
		int requiredTicks
) {
	public long elapsed(long now) {
		return now - startTick;
	}

	public boolean ready(long now) {
		return elapsed(now) >= requiredTicks;
	}
}

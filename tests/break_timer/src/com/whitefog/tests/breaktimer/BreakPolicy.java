package com.whitefog.tests.breaktimer;

/**
 * Политика разрушения: ЧТО разрешено и СКОЛЬКО тиков это занимает.
 *
 * <p>Явно отделена от lifecycle-таймера: точные длительности и item id модового кайла
 * в проекте НЕ заданы (см. AGENTS.md «этап 1.3»). Политика инжектируется, поэтому
 * lifecycle можно доказать независимо от спорных геймплейных чисел.</p>
 */
public interface BreakPolicy {

	/** Результат проверки входа. */
	enum Decision {
		ALLOW,
		/** Твёрдый/станция без подходящего инструмента → «Слишком крепко — нужен инструмент». */
		DENY_NO_TOOL,
		/** Горячая/работающая станция — сначала остывает. */
		DENY_HOT,
		/** Наполненная станция/ёмкость — сначала опустошить (содержимое не теряется). */
		DENY_FILLED
	}

	/** Маппинг {@code ItemStack} → категория инструмента. */
	ToolKind classify(ToolStack tool);

	/** Можно ли начать разрушение данным инструментом. */
	Decision evaluateStart(BlockKind kind, ToolStack tool, ToolKind toolKind);

	/** Требуемая длительность в тиках. */
	int requiredTicks(BlockKind kind, ToolKind toolKind);

	/** Сколько предметов выпадает при успешном commit (0 = без дропа). */
	int dropsOnCommit(BlockKind kind, ToolKind toolKind);

	/** Cooldown подсказки об отказе, в тиках. */
	long messageCooldownTicks();
}

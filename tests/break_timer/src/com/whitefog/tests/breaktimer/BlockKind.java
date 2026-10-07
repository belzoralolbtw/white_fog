package com.whitefog.tests.breaktimer;

/**
 * Категории блоков из ROADMAP_STEPS.md, этап 1.3.
 *
 * <p>Это классификация правил, а не реальный {@code Block}. Прототип проверяет,
 * что lifecycle-таймер корректно реагирует на каждую категорию.</p>
 */
public enum BlockKind {
	/** Трава, цветы, саженцы, грибы — мягкие растения. */
	SOFT_PLANT,
	/** Листва (рукой без дропа). */
	SOFT_LEAF,
	/** Земля/песок/гравий/глина — рукой медленно, лопатой быстро. */
	SOFT_SOIL,
	/** Брёвна. */
	HARD_WOOD,
	/** Камень, руды, кирпич, стекло. */
	HARD_STONE,
	/** Металл. */
	HARD_METAL,
	/** Деревянные станции: crafting_table, loom (рука/топор). */
	STATION_WOOD,
	/** Печь (не горит) — только модовый кайло. */
	STATION_FURNACE,
	/** Наковальня — только модовый кайло. */
	STATION_ANVIL,
	/** Пустой котёл — только модовый кайло. */
	STATION_CAULDRON_EMPTY,
	/** Работающая/горячая станция (зажжённая печь, костёр). */
	STATION_HOT,
	/** Котёл с содержимым (вода/лава) — отказ до опустошения. */
	CAULDRON_FILLED,
	/** Фонарь — снимается рукой. */
	LANTERN;

	/** Горячая станция: снятие запрещено, пока не остынет. */
	public boolean isHot() {
		return this == STATION_HOT;
	}

	/** Является ли станция (для отдельной обработки содержимого и одного дропа). */
	public boolean isStation() {
		return this == STATION_WOOD
				|| this == STATION_FURNACE
				|| this == STATION_ANVIL
				|| this == STATION_CAULDRON_EMPTY
				|| this == STATION_HOT
				|| this == CAULDRON_FILLED;
	}
}

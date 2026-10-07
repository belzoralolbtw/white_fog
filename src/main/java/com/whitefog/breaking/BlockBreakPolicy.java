package com.whitefog.breaking;

import com.whitefog.WhiteFogConfig;

/**
 * Политика разрушения: что разрешено и сколько тиков это занимает (этап 1.3).
 *
 * <p>Отделена от lifecycle-таймера ({@link BreakTimerService}): все длительности и запреты
 * берутся из {@link WhiteFogConfig} (значения утверждены ROADMAP_STEPS.md).</p>
 */
public final class BlockBreakPolicy {
	private BlockBreakPolicy() {
	}

	/** Решение по попытке начать разрушение. */
	public enum Decision {
		/** Разрешено. */
		ALLOW,
		/** Твёрдое/станция без подходящего инструмента → {@code Слишком крепко — нужен инструмент}. */
		DENY_NO_TOOL,
		/** Горячая/работающая станция — сначала остывает. */
		DENY_HOT,
		/** Наполненная станция/ёмкость — сначала опустошить содержимое. */
		DENY_FILLED
	}

	/** Можно ли начать разрушение данным инструментом. */
	public static Decision evaluateStart(BlockBreakRules.Category category, BlockBreakRules.ToolKind tool) {
		return switch (category) {
			// Мягкие: руками/любым инструментом.
			case SOFT_PLANT, SOFT_LEAF, SOFT_SOIL, SOFT_ICE, SOFT_SNOW, LANTERN -> Decision.ALLOW;
			// Деревянные станции: рука или топор.
			case STATION_WOOD -> (tool == BlockBreakRules.ToolKind.HAND
					|| tool == BlockBreakRules.ToolKind.VANILLA_AXE) ? Decision.ALLOW : Decision.DENY_NO_TOOL;
			// Твёрдые станции: только модовое кайло.
			case STATION_HARD -> tool.isModPickaxe() ? Decision.ALLOW : Decision.DENY_NO_TOOL;
			// Твёрдые категории: только модовое кайло (ванильные кирка/топор запрещены).
			case HARD -> tool.isModPickaxe() ? Decision.ALLOW : Decision.DENY_NO_TOOL;
			case STATION_HOT -> Decision.DENY_HOT;
			case STATION_FILLED -> Decision.DENY_FILLED;
			// Не покрыто — решение не участвует (ванильное поведение).
			case UNCLASSIFIED -> Decision.ALLOW;
		};
	}

	/**
	 * Требуемая длительность в игровых тиках.
	 *
	 * <p>Дефолты обязательны для тестов: мягкое 40, земля/песок/гравий рукой 60 и лопатой 10,
	 * лёд 80, станция 40, твёрдое модовым кайлом 40–120 по tier.</p>
	 */
	public static int requiredTicks(BlockBreakRules.Category category, BlockBreakRules.ToolKind tool) {
		return switch (category) {
			case SOFT_PLANT, SOFT_LEAF, SOFT_SNOW, LANTERN -> WhiteFogConfig.BREAK_SOFT_DEFAULT_TICKS;
			case SOFT_ICE -> WhiteFogConfig.BREAK_SOFT_ICE_TICKS;
			case SOFT_SOIL -> tool == BlockBreakRules.ToolKind.VANILLA_SHOVEL
					? WhiteFogConfig.BREAK_SOFT_SOIL_SHOVEL_TICKS
					: WhiteFogConfig.BREAK_SOFT_SOIL_HAND_TICKS;
			case STATION_WOOD, STATION_HARD -> WhiteFogConfig.BREAK_STATION_TICKS;
			case HARD -> switch (tool) {
				case MOD_PICKAXE_BRONZE -> WhiteFogConfig.BREAK_HARD_BRONZE_TICKS;
				case MOD_PICKAXE_IRON -> WhiteFogConfig.BREAK_HARD_IRON_TICKS;
				case MOD_PICKAXE_STEEL -> WhiteFogConfig.BREAK_HARD_STEEL_TICKS;
				// Доступ сюда невозможен (HARD требует модовое кайло), но оставляем безопасный дефолт.
				default -> WhiteFogConfig.BREAK_HARD_BRONZE_TICKS;
			};
			case STATION_HOT, STATION_FILLED, UNCLASSIFIED -> WhiteFogConfig.BREAK_SOFT_DEFAULT_TICKS;
		};
	}

	/**
	 * Даёт ли успешное разрушение ровно 0 дропа.
	 *
	 * <p>Листва и мягкие растения (трава/цветы/саженцы/грибы) по ROADMAP_STEPS.md
	 * дают 0 дропа; скрытый ванильный дроп (в том числе ножницами/топором) не выдаётся.
	 * Дроп с листвы появится отдельной ПКМ-механикой этапа 3.1.</p>
	 */
	public static boolean dropsZero(BlockBreakRules.Category category) {
		return category == BlockBreakRules.Category.SOFT_PLANT
				|| category == BlockBreakRules.Category.SOFT_LEAF;
	}
}

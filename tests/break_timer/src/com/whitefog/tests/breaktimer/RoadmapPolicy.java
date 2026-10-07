package com.whitefog.tests.breaktimer;

/**
 * Политика по правилам ROADMAP_STEPS.md, этап 1.3.
 *
 * <p>Длительности и item id кайла теперь совпадают с утверждёнными значениями и
 * перенесены в {@code WhiteFogConfig} на этапе применения в {@code src}. Здесь они
 * продублированы как константы sandbox (у sandbox нет зависимости от Minecraft).</p>
 */
public final class RoadmapPolicy implements BreakPolicy {

	// --- Утверждённые значения (совпадают с WhiteFogConfig этапа 1.3) ---
	private static final int T_SOFT_DEFAULT = 40;
	private static final int T_SOFT_SOIL_HAND = 60;
	private static final int T_SOFT_SOIL_SHOVEL = 10;
	private static final int T_SOFT_ICE = 80;
	private static final int T_HARD_BRONZE = 120;
	private static final int T_HARD_IRON = 80;
	private static final int T_HARD_STEEL = 40;
	private static final int T_STATION = 40;
	private static final long MESSAGE_COOLDOWN = 20L;

	/** Exact id модового кайла tier BRONZE (используется в тесте; регистрация — этап 6.4). */
	public static final String MOD_PICKAXE_ID = "white_fog:bronze_pickaxe";
	/** Exact id модового кайла tier IRON. */
	public static final String MOD_PICKAXE_IRON_ID = "white_fog:iron_pickaxe";
	/** Exact id модового кайла tier STEEL. */
	public static final String MOD_PICKAXE_STEEL_ID = "white_fog:steel_pickaxe";

	@Override
	public ToolKind classify(ToolStack tool) {
		if (tool == null || tool.isHand()) {
			return ToolKind.HAND;
		}
		String id = tool.itemId();
		if (id.equals(MOD_PICKAXE_ID) || id.equals(MOD_PICKAXE_IRON_ID) || id.equals(MOD_PICKAXE_STEEL_ID)) {
			return ToolKind.MOD_PICKAXE;
		}
		if (id.equals("minecraft:shears")) {
			return ToolKind.SHEARS;
		}
		if (id.startsWith("minecraft:") && id.endsWith("_pickaxe")) {
			return ToolKind.VANILLA_PICKAXE;
		}
		if (id.startsWith("minecraft:") && id.endsWith("_axe")) {
			return ToolKind.VANILLA_AXE;
		}
		if (id.startsWith("minecraft:") && id.endsWith("_shovel")) {
			return ToolKind.VANILLA_SHOVEL;
		}
		return ToolKind.HAND;
	}

	@Override
	public Decision evaluateStart(BlockKind kind, ToolStack tool, ToolKind toolKind) {
		if (kind.isHot()) {
			return Decision.DENY_HOT;
		}
		if (kind == BlockKind.CAULDRON_FILLED) {
			// Наполненные станции/ёмкости: отказ до опустошения (утверждено этап 1.3).
			return Decision.DENY_FILLED;
		}
		return switch (kind) {
			// Руками медленно разрешены; лопата/ножницы ускоряют.
			case SOFT_PLANT, SOFT_LEAF, SOFT_SOIL, LANTERN -> Decision.ALLOW;
			// Деревянные станции: рука/топор.
			case STATION_WOOD ->
					(toolKind == ToolKind.HAND || toolKind == ToolKind.VANILLA_AXE)
							? Decision.ALLOW : Decision.DENY_NO_TOOL;
			// Печь/наковальня/пустой котёл — только модовое кайло.
			case STATION_FURNACE, STATION_ANVIL, STATION_CAULDRON_EMPTY ->
					toolKind == ToolKind.MOD_PICKAXE ? Decision.ALLOW : Decision.DENY_NO_TOOL;
			// Твёрдые категории: только модовое кайло (ванильные кирка/топор запрещены).
			case HARD_WOOD, HARD_STONE, HARD_METAL ->
					toolKind == ToolKind.MOD_PICKAXE ? Decision.ALLOW : Decision.DENY_NO_TOOL;
			default -> Decision.DENY_NO_TOOL;
		};
	}

	@Override
	public int requiredTicks(BlockKind kind, ToolKind toolKind) {
		return switch (kind) {
			case SOFT_PLANT, SOFT_LEAF, LANTERN -> T_SOFT_DEFAULT;
			case SOFT_SOIL -> toolKind == ToolKind.VANILLA_SHOVEL ? T_SOFT_SOIL_SHOVEL : T_SOFT_SOIL_HAND;
			case HARD_WOOD, HARD_STONE, HARD_METAL -> switch (toolKind) {
				case MOD_PICKAXE -> T_HARD_BRONZE;
				default -> T_HARD_BRONZE;
			};
			case STATION_WOOD, STATION_FURNACE, STATION_ANVIL, STATION_CAULDRON_EMPTY -> T_STATION;
			default -> T_SOFT_DEFAULT;
		};
	}

	@Override
	public int dropsOnCommit(BlockKind kind, ToolKind toolKind) {
		return switch (kind) {
			// Листва и мягкие растения: 0 дропа при ручном разрушении (утверждено этап 1.3);
			// дроп с листвы — только отдельная ПКМ-механика этапа 3.1.
			case SOFT_LEAF, SOFT_PLANT -> 0;
			case CAULDRON_FILLED -> 0; // до станции дело не дойдёт (DENY_FILLED)
			default -> 1;
		};
	}

	@Override
	public long messageCooldownTicks() {
		return MESSAGE_COOLDOWN;
	}
}

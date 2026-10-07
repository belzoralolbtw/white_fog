package com.whitefog.breaking;

import com.whitefog.WhiteFogConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Классификация блоков и инструментов для правил разрушения (этап 1.3).
 *
 * <p>Здесь только «ЧТО» за блок/инструмент; длительности и решения — в
 * {@link BlockBreakPolicy}. Все идентификаторы тегов проверены по реальным деобфусцированным
 * джарникам Minecraft 26.2 ({@code BlockTags}/{@code ItemTags}/{@code Blocks}) и по содержимому
 * {@code data/minecraft/tags/block/*.json} из 26.2 deobf jar.</p>
 *
 * <p><b>Источники-референсы (адаптировано, не скопировано):</b></p>
 * <ul>
 *     <li>{@code Patbox/polymer} ({@code dev/26.2}) —
 *         {@code polymer-core/.../mixin/block/ServerPlayerGameModeMixin.java}: серверный перехват
 *         {@code handleBlockBreakAction} и ручная выдача блока при снятии.</li>
 *     <li>{@code Tschipp/CarryOn} ({@code 26.2}) — {@code Common/.../carry/PickupHandler.java}:
 *         снятие блока со станции без дублей — {@code removeBlockEntity} + {@code removeBlock},
 *         проверка дистанции и сохранение BlockEntity.</li>
 * </ul>
 */
public final class BlockBreakRules {
	private BlockBreakRules() {
	}

	/**
	 * Категория блока по правилам этапа 1.3.
	 *
	 * <p>Порядок важен: сначала станции и «горячие/наполненные», затем мягкие блоки
	 * (лёд/снег указаны явно раньше тега {@code mineable/pickaxe}, в который входит лёд),
	 * и только потом твёрдые категории.</p>
	 */
	public enum Category {
		/** Трава, цветы, саженцы, грибы: рукой медленно, дроп 0 (этап 1.3). */
		SOFT_PLANT,
		/** Листва: рукой медленно, дроп 0 до отдельной ПКМ-механики этапа 3.1. */
		SOFT_LEAF,
		/** Земля/песок/гравий/глина: рукой 60, лопатой 10, обычный ванильный лут. */
		SOFT_SOIL,
		/** Лёд (ice/packed_ice/blue_ice/frosted_ice): рукой 80. */
		SOFT_ICE,
		/** Снег/снежный блок: рукой 40. */
		SOFT_SNOW,
		/** Фонарь/душевный фонарь: снимается рукой. */
		LANTERN,
		/** Деревянная станция: crafting_table, loom (рука/топор). */
		STATION_WOOD,
		/** Твёрдая станция (не горит, пустая): furnace/blast_furnace/smoker/cauldron/anvil — только модовое кайло. */
		STATION_HARD,
		/** Горячая/работающая станция: снятие запрещено до фактического остывания. */
		STATION_HOT,
		/** Наполненная станция/ёмкость: сначала опустошить. */
		STATION_FILLED,
		/** Твёрдые категории: брёвна, доски, камень, руды, кирпич, стекло, металл — только модовое кайло. */
		HARD,
		/** Не покрыто правилами: обычное ванильное поведение. */
		UNCLASSIFIED;

		/** Является ли категория снимаемой станцией (отдельная выдача ровно одного предмета). */
		public boolean isStation() {
			return this == STATION_WOOD || this == STATION_HARD;
		}

		/** Является ли категория мягкой (руками разрешена). */
		public boolean isSoft() {
			return this == SOFT_PLANT || this == SOFT_LEAF || this == SOFT_SOIL
					|| this == SOFT_ICE || this == SOFT_SNOW || this == LANTERN;
		}
	}

	/** Категория инструмента, влияющая на правила 1.3. */
	public enum ToolKind {
		/** Пустая рука. */
		HAND,
		/** Ванильная кирка (любой материал) — не должна добывать твёрдые категории. */
		VANILLA_PICKAXE,
		/** Ванильный топор — не должен добывать брёвна/доски. */
		VANILLA_AXE,
		/** Ванильная лопата — ускоряет мягкий грунт. */
		VANILLA_SHOVEL,
		/** Любой прочий предмет. */
		OTHER,
		/** Модовое кайло tier BRONZE (id проверяется по строке, предмет регистрируется на этапе 6.4). */
		MOD_PICKAXE_BRONZE,
		/** Модовое кайло tier IRON (регистрация — этап 6.4). */
		MOD_PICKAXE_IRON,
		/** Модовое кайло tier STEEL (регистрация — этап 6.4). */
		MOD_PICKAXE_STEEL;

		/** Является ли инструмент модовым кайлом любого tier. */
		public boolean isModPickaxe() {
			return this == MOD_PICKAXE_BRONZE || this == MOD_PICKAXE_IRON || this == MOD_PICKAXE_STEEL;
		}
	}

	// ------------------------------------------------------------------
	// Классификация блока
	// ------------------------------------------------------------------

	/**
	 * Определяет категорию блока в точке {@code pos}.
	 *
	 * <p>Принимает {@link Level} (а не {@code ServerLevel}), чтобы одна и та же классификация
	 * использовалась И сервером, И клиентом (hotfix этапа 1.3: клиентское предсказание обязано
	 * принимать то же решение, что и сервер). Это безопасное обобщение: серверная семантика
	 * не меняется ({@code ServerLevel} — подкласс {@code Level}), а {@code common} не получает
	 * ссылок на {@code net.minecraft.client.*}.</p>
	 *
	 * <p>На сервере содержимое/горячесть читаются из реальных данных; на клиенте —
	 * из синхронизированного состояния (см. {@link #isHot(BlockState)},
	 * {@link #isFilled(Level, BlockPos, BlockState)}).</p>
	 */
	public static Category classify(Level level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return Category.UNCLASSIFIED;
		}
		Block block = state.getBlock();

		// Блоки-инструменты оператора (barrier, command/structure/test blocks): не трогаем,
		// пусть ванильный handleBlockBreakAction сам проверит canUseGameMasterBlocks.
		// (barrier входит в тег IMPERMEABLE, поэтому исключение обязательно до проверки стекла.)
		if (block instanceof GameMasterBlock) {
			return Category.UNCLASSIFIED;
		}
		// Неразрушимые блоки (bedrock, end_portal_frame, portal и т.п., destroySpeed = -1):
		// не покрываем правилами — ванильное поведение (сломать всё равно нельзя).
		if (state.getDestroySpeed(level, pos) < 0.0F) {
			return Category.UNCLASSIFIED;
		}

		// --- Станции: сначала, иначе MINEABLE_WITH_PICKAXE перехватит печь/наковальню/котёл. ---
		if (block instanceof AbstractFurnaceBlock) {
			if (state.getValue(AbstractFurnaceBlock.LIT)) {
				return Category.STATION_HOT;
			}
			return isFilled(level, pos, state) ? Category.STATION_FILLED : Category.STATION_HARD;
		}
		if (block instanceof CampfireBlock) {
			// Костёр не входит в список снимаемых станций; горящий — отказ, остывший — обычная ваниль.
			return CampfireBlock.isLitCampfire(state) ? Category.STATION_HOT : Category.UNCLASSIFIED;
		}
		if (block instanceof AbstractCauldronBlock cauldron) {
			return cauldron.isFull(state) ? Category.STATION_FILLED : Category.STATION_HARD;
		}
		if (block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) {
			return Category.STATION_HARD;
		}
		if (block == Blocks.CRAFTING_TABLE || block == Blocks.LOOM) {
			return Category.STATION_WOOD;
		}
		if (block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN) {
			return Category.LANTERN;
		}

		// --- Мягкие: снег и лёд раньше тега mineable/pickaxe (лёд в нём состоит). ---
		if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK) {
			return Category.SOFT_SNOW;
		}
		if (state.is(BlockTags.ICE)) {
			return Category.SOFT_ICE;
		}
		if (state.is(BlockTags.LEAVES)) {
			return Category.SOFT_LEAF;
		}
		if (isSoftPlant(block, state)) {
			return Category.SOFT_PLANT;
		}
		if (isSoftSoil(block)) {
			return Category.SOFT_SOIL;
		}

		// --- Твёрдые: стекло (вне requiresCorrectToolForDrops, ломается рукой),
		//     брёвна/доски (руками добываются) и все блоки, требующие правильный инструмент
		//     для дропа: камень, руды, кирпич, металл, тяжёлые механизмы.
		//     Декоративные блоки без требования инструмента (рельсы, кнопки, таблички,
		//     деревянные двери/ступени) остаются UNCLASSIFIED и работают как в ванили. ---
		if (state.is(BlockTags.IMPERMEABLE)) {
			return Category.HARD;
		}
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
			return Category.HARD;
		}
		if (state.requiresCorrectToolForDrops()) {
			return Category.HARD;
		}
		return Category.UNCLASSIFIED;
	}

	/**
	 * Мягкие растения: трава (short/tall grass, fern), цветы (тег {@code flowers}),
	 * саженцы (id оканчивается на {@code _sapling}) и грибы.
	 */
	private static boolean isSoftPlant(Block block, BlockState state) {
		if (state.is(BlockTags.FLOWERS)) {
			return true;
		}
		if (block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS
				|| block == Blocks.FERN || block == Blocks.LARGE_FERN
				|| block == Blocks.DEAD_BUSH
				|| block == Blocks.BROWN_MUSHROOM || block == Blocks.RED_MUSHROOM) {
			return true;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return id != null && id.getPath().endsWith("_sapling");
	}

	/** Мягкий грунт: земля/песок/гравий/глина (рукой медленно, лопатой быстро). */
	private static boolean isSoftSoil(Block block) {
		return block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
				|| block == Blocks.CLAY || block == Blocks.SAND || block == Blocks.RED_SAND
				|| block == Blocks.GRAVEL;
	}

	/** Горячая ли станция (печь с {@code LIT=true}, зажжённый костёр). */
	public static boolean isHot(BlockState state) {
		if (state.getBlock() instanceof AbstractFurnaceBlock) {
			return state.getValue(AbstractFurnaceBlock.LIT);
		}
		if (state.getBlock() instanceof CampfireBlock) {
			return CampfireBlock.isLitCampfire(state);
		}
		return false;
	}

	/**
	 * Наполнена ли станция/ёмкость ПРЯМО СЕЙЧАС (не по старой категории).
	 *
	 * <p>Критично для этапа 1.3: содержимое печи можно добавить за время таймера без смены
	 * {@code BlockState} ({@code LIT=false}), поэтому наполненность перепроверяется на каждом
	 * tick/STOP/commit, а не только при старте. Котёл считается наполненным по {@code isFull}
	 * (вода/лава/порошковый снег).</p>
	 */
	public static boolean isFilled(Level level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof AbstractCauldronBlock cauldron) {
			return cauldron.isFull(state);
		}
		if (state.hasBlockEntity()) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			return blockEntity instanceof Container container && !container.isEmpty();
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Классификация инструмента
	// ------------------------------------------------------------------

	/**
	 * Определяет категорию инструмента по точному id предмета и item-тегам.
	 *
	 * <p>Модовые кайла проверяются по точному id ({@code WhiteFogConfig.BREAK_MOD_PICKAXE_*})
	 * до ванильных тегов: до этапа 6.4 предметы не зарегистрированы, но правила компилируются
	 * и будут работать сразу после их появления. У {@code ItemStack} в 26.2 нет {@code is(TagKey)} —
	 * тег проверяется через {@code typeHolder().is(ItemTags.*)}.</p>
	 */
	public static ToolKind classifyTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ToolKind.HAND;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String itemId = id == null ? "" : id.toString();
		if (WhiteFogConfig.BREAK_MOD_PICKAXE_BRONZE.equals(itemId)) {
			return ToolKind.MOD_PICKAXE_BRONZE;
		}
		if (WhiteFogConfig.BREAK_MOD_PICKAXE_IRON.equals(itemId)) {
			return ToolKind.MOD_PICKAXE_IRON;
		}
		if (WhiteFogConfig.BREAK_MOD_PICKAXE_STEEL.equals(itemId)) {
			return ToolKind.MOD_PICKAXE_STEEL;
		}
		if (stack.typeHolder().is(ItemTags.PICKAXES)) {
			return ToolKind.VANILLA_PICKAXE;
		}
		if (stack.typeHolder().is(ItemTags.AXES)) {
			return ToolKind.VANILLA_AXE;
		}
		if (stack.typeHolder().is(ItemTags.SHOVELS)) {
			return ToolKind.VANILLA_SHOVEL;
		}
		return ToolKind.OTHER;
	}
}

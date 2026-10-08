package com.whitefog.darkness.light;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import com.whitefog.darkness.light.LightFuelPolicy.SourceKind;

/**
 * Мост между vanilla-блоками источников света и чистой политикой {@link LightFuelPolicy} (этап 1.6).
 *
 * <p>Своих Block/Item ID мод не регистрирует: используются vanilla {@code minecraft:torch}/
 * {@code wall_torch}, {@code soul_torch}/{@code soul_wall_torch}, {@code lantern}/{@code soul_lantern},
 * {@code campfire}/{@code soul_campfire}. Для torch/lantern-классов добавляется ровно одно
 * boolean-свойство {@link #WHITE_FOG_LIT} (см. миксин {@code BlockStateDefinitionMixin}); костёр
 * использует существующий {@link CampfireBlock#LIT}.</p>
 *
 * <p>Класс намеренно НЕ ссылается на {@code Blocks.*} в статических инициализаторах: миксины
 * обращаются к {@link #WHITE_FOG_LIT} ещё во время {@code Blocks.<clinit>}, когда поля
 * {@code Blocks.TORCH} и т.п. ещё не присвоены. Проверки блоков выполняются только в методах,
 * вызываемых после инициализации реестра.</p>
 */
public final class LightSourceBlocks {
	/** Добавленное свойство «источник горит» для torch/wall_torch/lantern/soul_lantern. */
	public static final BooleanProperty WHITE_FOG_LIT = BooleanProperty.create(LightConfig.LIT_PROPERTY_NAME);

	private LightSourceBlocks() {
	}

	/** Есть ли у состояния добавленное свойство {@code white_fog_lit}. */
	public static boolean hasLitProperty(BlockState state) {
		return state.hasProperty(WHITE_FOG_LIT);
	}

	/**
	 * Вид поддерживаемого источника или {@code null}, если блок не поддерживается.
	 * Настенные варианты torch отображаются в тот же вид, что и стоячие.
	 */
	public static SourceKind kind(BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.TORCH || block == Blocks.WALL_TORCH) {
			return SourceKind.TORCH;
		}
		if (block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH) {
			return SourceKind.SOUL_TORCH;
		}
		if (block == Blocks.LANTERN) {
			return SourceKind.LANTERN;
		}
		if (block == Blocks.SOUL_LANTERN) {
			return SourceKind.SOUL_LANTERN;
		}
		if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
			return SourceKind.CAMPFIRE;
		}
		return null;
	}

	/** Поддерживается ли блок как гаснущий источник. */
	public static boolean isManaged(BlockState state) {
		return kind(state) != null;
	}

	/**
	 * Является ли предмет BlockItem-предметом поддерживаемого источника (torch/lantern/campfire).
	 * Нужен серверному тику топлива в инвентаре: компонент {@code white_fog:light_fuel} может
	 * лежать на любом предмете-источнике (не только на факеле в левой руке).
	 */
	public static boolean isManagedItem(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		return kind(blockItem.getBlock().defaultBlockState()) != null;
	}

	/** Горит ли источник сейчас (костёр — по LIT, остальные — по {@code white_fog_lit}). */
	public static boolean isLit(BlockState state) {
		if (state.getBlock() == Blocks.CAMPFIRE || state.getBlock() == Blocks.SOUL_CAMPFIRE) {
			return state.getValue(CampfireBlock.LIT);
		}
		if (state.hasProperty(WHITE_FOG_LIT)) {
			return state.getValue(WHITE_FOG_LIT);
		}
		return true;
	}

	/** Возвращает состояние с заданным lit (для костра — LIT, иначе {@code white_fog_lit}). */
	public static BlockState withLit(BlockState state, boolean lit) {
		if (state.getBlock() == Blocks.CAMPFIRE || state.getBlock() == Blocks.SOUL_CAMPFIRE) {
			return state.setValue(CampfireBlock.LIT, lit);
		}
		if (state.hasProperty(WHITE_FOG_LIT)) {
			return state.setValue(WHITE_FOG_LIT, lit);
		}
		return state;
	}

	/** Ёмкость источника в тиках. */
	public static int capacity(SourceKind kind) {
		return LightFuelPolicy.capacityTicks(kind);
	}

	/** Ёмкость состояния (или 0, если не поддерживается). */
	public static int capacity(BlockState state) {
		SourceKind kind = kind(state);
		return kind == null ? 0 : capacity(kind);
	}

	/** Точный id блока состояния (для поля {@code expectedBlockId} записи хранилища). */
	public static String blockId(BlockState state) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id == null ? "" : id.toString();
	}

	/** Совпадает ли фактический блок с ожидаемым id записи. */
	public static boolean matchesExpected(BlockState state, String expectedBlockId) {
		if (expectedBlockId == null) {
			return false;
		}
		return blockId(state).equals(expectedBlockId);
	}
}

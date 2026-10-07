package com.whitefog.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * «Камушка» ({@code white_fog:small_stone}) — обычный собственный блок, НЕ decor entity (этап 1.4).
 *
 * <p>Паспорт: stack 64, масса 0.2 кг, без прочности; приземистый неровный камень, два варианта
 * block model со сдвинутыми выступами, текстура общая. Selection box {@code x/z 5..11, y 0..3};
 * collision пустая — игрок не спотыкается. Один блок = одна камушка, количества внутри блока нет.
 * Вариант внешнего вида ({@link #VARIANT}) детерминирован по позиции при установке/генерации и
 * НЕ влияет на loot. Отдельного {@code small_stone_decor} entity ID нет; block entity у камушка нет.</p>
 *
 * <p>ЛКМ: камушек — обычный {@code instabreak} блок, поэтому ванильный ломающий путь даёт
 * ровно один предмет через block loot. ПКМ-подбор обрабатывается отдельно (main-hand) и
 * подавляет block loot (см. {@code com.whitefog.station.SmallStonePickup}).</p>
 */
public class SmallStoneBlock extends Block {
	/** Детерминированный вариант модели: два набора сдвинутых выступов. */
	public static final BooleanProperty VARIANT = BooleanProperty.create("variant");

	private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 3.0D, 11.0D);

	public SmallStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(VARIANT, Boolean.FALSE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(VARIANT);
	}

	/**
	 * Вариант определяется позицией (детерминирован) и не участвует в loot.
	 * Hook point для worldgen этапа 2.1: генерация использует этот же метод, поэтому вариант
	 * стабилен при повторной загрузке и не требует хранения.
	 */
	public static boolean variantFor(BlockPos pos) {
		return ((pos.getX() ^ pos.getZ()) & 1) == 0;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(VARIANT, variantFor(context.getClickedPos()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// Collision пустая: игрок не спотыкается о камушек.
		return Shapes.empty();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && !canSurvive(state, level, pos)) {
			level.destroyBlock(pos, true, null, 512);
		}
	}
}

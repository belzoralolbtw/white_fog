package com.whitefog.content.block;

import com.whitefog.content.block.entity.FlatStoneBlockEntity;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * «Плоский камень» ({@code white_fog:flat_stone}) — переносимая станция (этап 1.4).
 *
 * <p>Паспорт: stack 16, масса 3.0 кг, без прочности; непрозрачная каменная текстура,
 * модель из 3–5 прямоугольных элементов (высота 4/16, а не полблока), collision/selection —
 * единая коробка {@code x/z 1..15, y 0..4}. Поворот {@link #FACING} меняет ориентацию сколов,
 * но не рецепт; waterlogging не поддерживается; в жидкости установка запрещена (см. предмет).</p>
 *
 * <p>Block entity есть только у станции. Опора обязательна: {@link #canSurvive} требует
 * полную твёрдую опору снизу, а {@link #neighborChanged} при её потере снимает блок
 * обычным однократным block loot. Содержимое станции (escrow/output) выдаётся отдельно
 * в {@code FlatStoneBlockEntity#preRemoveSideEffects} (штатный 26.2-хук удаления block entity,
 * как у контейнеров) — block item при этом не дублируется.</p>
 *
 * <p><b>Источники-референсы (адаптировано, не скопировано):</b>
 * {@code Tschipp/CarryOn} ({@code 26.2}) — {@code Common/.../carry/PickupHandler.java}:
 * безопасное снятие блока со станции без дублей ({@code removeBlockEntity} + {@code removeBlock});
 * {@code Patbox/polymer} ({@code dev/26.2}) — тайминг block-entity/ломания.</p>
 */
public class FlatStoneBlock extends BaseEntityBlock {
	public static final MapCodec<FlatStoneBlock> CODEC = simpleCodec(FlatStoneBlock::new);

	/** Ориентация сколов; рецепт не зависит от значения. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** Единая коробка x/z 1..15, y 0..4 (16-я сетка) — и selection, и collision. */
	private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 4.0D, 15.0D);

	public FlatStoneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/**
	 * Потеря опоры: снимаем станцию с обычным однократным loot (block loot, не pickup).
	 * Содержимое (escrow/output) выдаётся отдельно в remove-callback, но НЕ block item.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (!level.isClientSide() && !canSurvive(state, level, pos)) {
			level.destroyBlock(pos, true, null, 512);
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FlatStoneBlockEntity(pos, state);
	}
}

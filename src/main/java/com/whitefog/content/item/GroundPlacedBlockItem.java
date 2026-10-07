package com.whitefog.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockItem для блоков, которые ставятся только на верхнюю грань полной твёрдой опоры
 * (этап 1.4: {@code flat_stone}, {@code small_stone}).
 *
 * <p>Правила установки (утверждены ТЗ): ПКМ по ВЕРХНЕЙ стороне полной твёрдой опоры,
 * свободная заменяемая позиция над ней, отсутствие жидкости, наличие прав. Проверки
 * выполняются ДО {@code super.useOn}, поэтому неподходящее место не списывает предмет
 * («unsupported placement не списывает предмет»). Само размещение/расход/звук делает
 * ванильный {@link BlockItem#useOn} (creative не списывает).</p>
 *
 * <p>Серверная авторитетность: решение принимается и на клиенте, и на сервере по
 * синхронизированному состоянию; мутацию мира выполняет серверный вызов (клиентское
 * предсказание ванильного {@code BlockItem} затем подтверждается/откатывается сервером).</p>
 */
public class GroundPlacedBlockItem extends BlockItem {
	public GroundPlacedBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getClickedFace() != Direction.UP) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos support = context.getClickedPos();
		// Опора обязана быть полной твёрдой (верхняя грань), а не заменяемой.
		if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
			return InteractionResult.FAIL;
		}
		BlockPos placePos = support.above();
		BlockState target = level.getBlockState(placePos);
		if (!target.canBeReplaced() || target.liquid()) {
			return InteractionResult.FAIL;
		}
		Player player = context.getPlayer();
		if (player != null && !player.mayUseItemAt(placePos, Direction.UP, context.getItemInHand())) {
			return InteractionResult.FAIL;
		}
		return super.useOn(context);
	}
}

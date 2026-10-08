package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Добавляет boolean-свойство {@code white_fog_lit} в state definition стоячих факелов (этап 1.6).
 *
 * <p>{@code TorchBlock} не переопределяет {@code createBlockStateDefinition}, поэтому инъекция в
 * {@link Block} на {@code TAIL} покрывает {@code minecraft:torch}, {@code soul_torch} и (сознательно)
 * {@code copper_torch}: свойство получает каждый экземпляр {@link TorchBlock}. Проверки поведения на
 * сервере всё равно идут по точному id ({@code Blocks.TORCH}/{@code Blocks.SOUL_TORCH}), поэтому
 * незарегистрированные как источники блоки остаются полностью ванильными (default lit=true).</p>
 *
 * <p>Настенные варианты ({@code WallTorchBlock}) и фонари ({@code LanternBlock}) переопределяют
 * {@code createBlockStateDefinition} без вызова {@code super}, поэтому обрабатываются отдельными
 * миксинами. Красstone-факелы наследуют {@code BaseTorchBlock} (не {@link TorchBlock}) и не затрагиваются.</p>
 */
@Mixin(Block.class)
public abstract class BlockStateDefinitionMixin {

	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void whiteFog$addLitProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
		if ((Object) this instanceof TorchBlock) {
			builder.add(LightSourceBlocks.WHITE_FOG_LIT);
		}
	}
}

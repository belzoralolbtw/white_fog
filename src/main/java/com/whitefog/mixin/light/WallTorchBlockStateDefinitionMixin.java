package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Добавляет {@code white_fog_lit} настенным факелам (этап 1.6).
 *
 * <p>{@code WallTorchBlock} переопределяет {@code createBlockStateDefinition} без вызова {@code super},
 * поэтому инъекция в {@code Block} до него не доходит — свойство добавляется здесь (FACING уже добавлен
 * ванилью, порядок не важен).</p>
 */
@Mixin(WallTorchBlock.class)
public abstract class WallTorchBlockStateDefinitionMixin {

	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void whiteFog$addLitProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(LightSourceBlocks.WHITE_FOG_LIT);
	}
}

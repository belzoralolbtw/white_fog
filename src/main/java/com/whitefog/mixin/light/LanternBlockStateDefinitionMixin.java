package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Добавляет {@code white_fog_lit} фонарям (этап 1.6).
 *
 * <p>{@code LanternBlock} переопределяет {@code createBlockStateDefinition} без вызова {@code super};
 * свойство добавляется здесь. Поддерживаются только {@code minecraft:lantern}/{@code soul_lantern} —
 * медные фонари получают свойство, но никогда не гасятся (default lit=true), так как поведение
 * привязано к точному id блока.</p>
 */
@Mixin(LanternBlock.class)
public abstract class LanternBlockStateDefinitionMixin {

	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void whiteFog$addLitProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(LightSourceBlocks.WHITE_FOG_LIT);
	}
}

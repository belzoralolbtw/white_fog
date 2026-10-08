package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подавляет пламенные частицы стоячего факела, если {@code white_fog_lit=false} (этап 1.6).
 */
@Mixin(TorchBlock.class)
public abstract class TorchBlockMixin {

	@Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
	private void whiteFog$skipUnlitParticles(BlockState state, Level level, BlockPos pos, RandomSource random,
			CallbackInfo ci) {
		if (state.hasProperty(LightSourceBlocks.WHITE_FOG_LIT)
				&& !state.getValue(LightSourceBlocks.WHITE_FOG_LIT)) {
			ci.cancel();
		}
	}
}

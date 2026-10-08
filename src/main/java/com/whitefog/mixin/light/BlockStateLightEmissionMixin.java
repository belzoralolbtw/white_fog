package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceBlocks;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Гасит световую эмиссию unlit-источников (этап 1.6).
 *
 * <p>В 26.2 {@code lightEmission} — финальное поле состояния, вычисленное при построении из
 * {@code Properties#lightLevel}. Добавление свойства его не пересчитывает, поэтому переопределяем
 * {@link BlockBehaviour.BlockStateBase#getLightEmission()} на HEAD: если {@code white_fog_lit=false},
 * возвращаем 0 (light engine обновится ближайшим {@code setBlock}). Для lit-состояний и всех прочих
 * блоков поведение ванильное.</p>
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateLightEmissionMixin {

	@Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
	private void whiteFog$unlitEmission(CallbackInfoReturnable<Integer> cir) {
		BlockState self = (BlockState) (Object) this;
		if (self.hasProperty(LightSourceBlocks.WHITE_FOG_LIT)
				&& !self.getValue(LightSourceBlocks.WHITE_FOG_LIT)) {
			cir.setReturnValue(0);
		}
	}
}

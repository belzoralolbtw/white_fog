package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Установка костра (этап 1.6): пустой предмет ставит unlit-костёр (LIT=false), заряженный — lit.
 * Костёр использует существующее свойство {@code LIT}, новое свойство не добавляется.
 */
@Mixin(CampfireBlock.class)
public abstract class CampfireBlockPlacementMixin {

	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void whiteFog$applyFuelOnPlace(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
		cir.setReturnValue(LightSourceService.adjustPlacement(context, cir.getReturnValue()));
	}
}

package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Установка настенного факела: lit по компоненту {@code white_fog:light_fuel} (этап 1.6). */
@Mixin(WallTorchBlock.class)
public abstract class WallTorchBlockPlacementMixin {

	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void whiteFog$applyFuelOnPlace(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
		cir.setReturnValue(LightSourceService.adjustPlacement(context, cir.getReturnValue()));
	}
}

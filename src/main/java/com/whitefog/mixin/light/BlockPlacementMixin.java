package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Читает item-компонент {@code white_fog:light_fuel} при установке стоячего факела (этап 1.6):
 * пустой предмет даёт unlit, заряженный — lit; заряженный {@code count>1} отклоняется (возврат null).
 * {@code TorchBlock} не переопределяет {@code getStateForPlacement}, поэтому точка — {@link Block}.
 */
@Mixin(Block.class)
public abstract class BlockPlacementMixin {

	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void whiteFog$applyFuelOnPlace(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
		cir.setReturnValue(LightSourceService.adjustPlacement(context, cir.getReturnValue()));
	}
}

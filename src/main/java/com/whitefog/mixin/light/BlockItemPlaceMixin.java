package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Commit установки источника (этап 1.6): сразу создаёт store-запись (без генерационного бонуса),
 * поэтому позже ленивый scan чанка бонус такому источнику не выдаст.
 *
 * <p>HEAD сбрасывает зафиксированную установку, RETURN коммитит только успешную установку
 * ({@code InteractionResult#consumesAction()}).</p>
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {

	@Inject(method = "place", at = @At("HEAD"))
	private void whiteFog$clearPending(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		LightSourceService.clearPendingPlacement();
	}

	@Inject(method = "place", at = @At("RETURN"))
	private void whiteFog$commitPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		LightSourceService.commitPlacement(context.getLevel(), cir.getReturnValue());
	}
}

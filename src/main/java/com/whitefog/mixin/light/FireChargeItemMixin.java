package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.context.UseOnContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Огненный шар не зажигает костёр без топлива (этап 1.6): при remaining &le; 0 ванильный ignition
 * отклоняется (FAIL); при remaining &gt; 0 — только включает LIT, без добавления ticks.
 */
@Mixin(FireChargeItem.class)
public abstract class FireChargeItemMixin {

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void whiteFog$refuseEmptyCampfire(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getLevel() instanceof ServerLevel serverLevel
				&& LightSourceService.shouldRefuseCampfireIgnition(serverLevel, context.getClickedPos())) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}

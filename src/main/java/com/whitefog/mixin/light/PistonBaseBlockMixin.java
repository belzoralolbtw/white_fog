package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceBlocks;
import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Локальное правило «поршень не перемещает поддерживаемый источник света» (этап 1.6).
 *
 * <p>Запись хранилища привязана к позиции, поэтому перемещение источника сделало бы её некорректной.
 * Возврат {@code false} из {@code isPushable} только для позиций с store-записью; механизм снятия
 * этапа 1.3 не переписывается.</p>
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private static void whiteFog$noPushManagedSource(BlockState state, Level level, BlockPos pos, Direction direction,
			boolean allowDestroy, Direction pistonDirection, CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel serverLevel && LightSourceBlocks.isManaged(state)
				&& LightSourceService.store(serverLevel).get(pos) != null) {
			cir.setReturnValue(false);
		}
	}
}

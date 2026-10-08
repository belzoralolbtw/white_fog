package com.whitefog.mixin.light;

import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Дозапись remaining в единственный штатный drop (этап 1.6).
 *
 * <p>Точка {@code Block.popResource(Level, BlockPos, ItemStack)} вызывается штатным путём loot-дропа.
 * Если для позиции есть store-запись, remaining пишется в уже созданный стек, а запись удаляется.
 * Дополнительный предмет НЕ создаётся; второй и последующие {@code popResource} для той же позиции
 * записи уже не находят.</p>
 */
@Mixin(Block.class)
public abstract class BlockPopResourceMixin {

	@Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"))
	private static void whiteFog$fuelDrop(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		if (level instanceof ServerLevel serverLevel) {
			LightSourceService.onVanillaDrop(serverLevel, pos, stack);
		}
	}
}

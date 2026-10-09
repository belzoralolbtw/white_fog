package com.whitefog.mixin.shelter;

import com.whitefog.darkness.shelter.ShelterProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.2 returns old state only for a successful mutation; covers commands, redstone and players alike. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkShelterMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private void whitefog$shelterChanged(BlockPos pos, BlockState state, int flags,
                                        CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        if (old != null && old != state && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            ShelterProvider.blockChanged(level, pos);
        }
    }
}

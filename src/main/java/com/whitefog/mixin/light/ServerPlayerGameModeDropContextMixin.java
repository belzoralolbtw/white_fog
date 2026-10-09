package com.whitefog.mixin.light;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.whitefog.darkness.light.LightSourceService;
import com.whitefog.darkness.light.LightSourceBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** Captures vanilla player-break source state before ServerPlayerGameMode.destroyBlock removes it. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeDropContextMixin {
    @Shadow protected ServerLevel level;

    @WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z")
    private boolean whiteFog$withVanillaDropContext(BlockPos pos, Operation<Boolean> original) {
        BlockState state = this.level.getBlockState(pos);
        LightSourceService.DropContext previous = LightSourceService.pushDropContext(this.level, pos, state);
        try {
            return original.call(pos);
        } finally {
            LightSourceService.restoreDropContext(previous);
        }
    }
}

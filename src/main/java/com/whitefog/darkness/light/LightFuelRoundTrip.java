package com.whitefog.darkness.light;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Shared production conversion between a managed block state and its single item drop. */
public final class LightFuelRoundTrip {
    private LightFuelRoundTrip() { }

    public static boolean matchesItem(ItemStack stack, LightFuelPolicy.SourceKind expected) {
        if (stack == null || stack.isEmpty() || stack.getCount() != 1
                || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return false;
        return LightSourceBlocks.kind(item.getBlock().defaultBlockState()) == expected;
    }

    public static boolean writeDrop(ItemStack stack, BlockState sourceState, int remaining,
            LightFuelPolicy.SourceKind expected) {
        if (sourceState == null || LightSourceBlocks.kind(sourceState) != expected
                || !matchesItem(stack, expected)) return false;
        LightFuelComponent.writeFuel(stack, remaining, remaining > 0 && LightSourceBlocks.isLit(sourceState));
        return true;
    }

    public static BlockState applyItem(BlockState placement, ItemStack stack) {
        LightFuelPolicy.SourceKind kind = LightSourceBlocks.kind(placement);
        if (kind == null || LightFuelComponent.isInvalidChargedStack(stack, LightFuelComponent.readRemaining(stack))) {
            return placement;
        }
        LightFuelComponent.LightFuel fuel = LightFuelComponent.read(stack);
        int remaining = LightFuelPolicy.normalizeComponentValue(fuel.remainingTicks(), LightSourceBlocks.capacity(kind));
        return LightSourceBlocks.withLit(placement, remaining > 0 && fuel.lit());
    }
}

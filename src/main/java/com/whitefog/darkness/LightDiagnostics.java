package com.whitefog.darkness;

import com.whitefog.WhiteFogAttachments;
import com.whitefog.darkness.light.LightFuelComponent;
import com.whitefog.darkness.light.LightSourceService;
import com.whitefog.darkness.light.PortableLightService;
import com.whitefog.darkness.shelter.ShelterProvider;
import com.whitefog.state.PlayerSurvivalState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import java.util.Optional;

/** Read-only aggregate used by the dev command; gameplay services remain encapsulated. */
public final class LightDiagnostics {
    public record HandFuel(String item, int count, int remaining, boolean lit, boolean burning) { }
    public record CurrentInput(BlockPos eye, int vanillaBlockLight, int effectiveBlockLight,
            int portableEmission, boolean canSeeSky) { }
    public record Snapshot(PlayerSurvivalState state, LightExposureService.Diagnostic exposure,
            ShelterProvider.Diagnostic shelter, Optional<LightSourceService.Snapshot> nearest,
            HandFuel offhand, HandFuel mainhand, boolean darknessActive, int darknessRemaining,
            boolean speedRestricted, long serverTick, boolean cachePresent, CurrentInput current) { }

    private LightDiagnostics() { }

    public static Snapshot capture(MinecraftServer server, ServerPlayer player) {
        PlayerSurvivalState state = WhiteFogAttachments.peek(player);
        if (state == null) state = PlayerSurvivalState.createDefault();
        LightExposureService.Diagnostic exposure = LightExposureService.diagnostics(server, player);
        ShelterProvider.Diagnostic shelter = ShelterProvider.diagnostics(player);
        ServerLevel level = player.level();
        BlockPos currentEye = BlockPos.containing(player.getEyePosition());
        int currentVanilla = level.isLoaded(currentEye) ? level.getBrightness(LightLayer.BLOCK, currentEye) : 0;
        int currentPortable = PortableLightService.portableEmission(player);
        CurrentInput current = new CurrentInput(currentEye, currentVanilla,
                PortableLightService.effectiveBlockLight(player, currentVanilla), currentPortable,
                level.isLoaded(currentEye) && level.canSeeSky(currentEye));
        Optional<LightSourceService.Snapshot> nearest = LightSourceService.diagnosticNearest(level,
                player.getEyePosition(), 32.0D);
        MobEffectInstance darkness = player.getEffect(MobEffects.DARKNESS);
        var speedAttribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean speed = speedAttribute != null
                && speedAttribute.getModifier(com.whitefog.WhiteFog.id(DarknessConfig.SPEED_MODIFIER_PATH)) != null;
        return new Snapshot(state, exposure, shelter, nearest, hand(player.getOffhandItem()),
                hand(player.getMainHandItem()), darkness != null, darkness == null ? 0 : darkness.getDuration(),
                speed, server.getTickCount(), shelter != null && shelter.cachePresent(), current);
    }

    private static HandFuel hand(ItemStack stack) {
        LightFuelComponent.LightFuel fuel = LightFuelComponent.read(stack);
        boolean validKind = com.whitefog.darkness.light.LightSourceBlocks.isManagedItem(stack);
        boolean burning = validKind && stack.getCount() == 1 && fuel.burning();
        String item = stack.isEmpty() ? "пусто" : stack.getHoverName().getString();
        return new HandFuel(item, stack.getCount(), fuel.remainingTicks(), fuel.lit(), burning);
    }

    public static String pos(BlockPos pos) { return pos == null ? "нет" : pos.toShortString(); }

    public static String shelterReason(com.whitefog.darkness.shelter.ShelterSnapshot.Reason reason) {
        return reason + " (" + switch (reason) {
            case VALID -> "замкнутое укрытие";
            case UNLOADED -> "часть объёма не загружена";
            case OPEN_VOLUME -> "открытый объём или ошибка чтения";
            case TOO_LARGE -> "объём превышает лимит";
            case NO_FLOOR -> "нет герметичного пола";
            case NO_ROOF -> "нет герметичной крыши";
            case INVALID_START -> "неподходящая клетка ног";
            case TOO_SMALL -> "недостаточный объём/высота";
        } + ")";
    }

    /** Bounded DDA block targeting, stopping before any unloaded cell; no Level.clip chunk loading. */
    public static String lookedStation(ServerPlayer player) {
        var level = player.level();
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1.0F).scale(6.0D));
        return net.minecraft.world.level.BlockGetter.traverseBlocks(start, end, level, (world, position) -> {
            var chunk = world.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) return "цель=" + position + " loaded=false (чтение остановлено)";
            var state = chunk.getBlockState(position);
            if (state.isAir()) return null;
            var entity = chunk.getBlockEntities().get(position);
            if (entity instanceof com.whitefog.content.block.entity.FlatStoneBlockEntity station) {
                return "цель=" + position + " loaded=true station mode=" + station.mode() + " active="
                        + station.hasActiveJob() + " progress=" + station.jobProgressTicks() + "/" + station.jobRequiredTicks()
                        + " owner=" + station.jobOwner() + " escrow=" + station.escrow() + " output="
                        + station.output() + " revision=" + station.revision();
            }
            return "цель=" + position + " loaded=true block=" + state + " station=false";
        }, world -> "цель=нет в пределах 6 блоков");
    }
}

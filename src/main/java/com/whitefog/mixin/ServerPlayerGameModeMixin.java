package com.whitefog.mixin;

import com.whitefog.breaking.BreakTimerService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехват серверного разрушения блока (этап 1.3).
 *
 * <p>Единая серверная точка жизненного цикла разрушения в 26.2 —
 * {@link ServerPlayerGameMode#handleBlockBreakAction(BlockPos, ServerboundPlayerActionPacket.Action,
 * Direction, int, int)} (javap-проверено по 26.2 deobf jar). На {@code HEAD} отдаём действие
 * сервису {@link BreakTimerService}: {@code START_DESTROY_BLOCK}/{@code STOP_DESTROY_BLOCK}/
 * {@code ABORT_DESTROY_BLOCK}. Если сервис вернул {@code true}, ванильная обработка (прогресс и дроп)
 * отменяется полностью — сервер ведёт собственный таймер.</p>
 *
 * <p>Движок тика — единый {@code ServerTickEvents.END_SERVER_TICK} мода
 * ({@code WhiteFogServer} → {@link BreakTimerService#tickAll}), поэтому отдельный миксин на
 * {@code ServerPlayerGameMode#tick} не нужен.</p>
 *
 * <p><b>Источники-референсы (адаптировано, не скопировано):</b>
 * {@code Patbox/polymer} ({@code dev/26.2}) —
 * {@code polymer-core/.../mixin/block/ServerPlayerGameModeMixin.java} (серверный {@code @Inject}
 * в {@code handleBlockBreakAction});
 * {@code FabricMC/fabric-api} — {@code fabric-events-interaction-v0/.../ServerPlayerGameModeMixin.java}
 * (та же точка входа и проверка дистанции).</p>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow
	protected ServerPlayer player;

	@Shadow
	protected ServerLevel level;

	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
	private void whiteFog$interceptBlockBreak(BlockPos pos, ServerboundPlayerActionPacket.Action action,
			Direction direction, int maxHeight, int sequence, CallbackInfo ci) {
		if (BreakTimerService.handleAction(this.player, this.level, pos, action, sequence)) {
			ci.cancel();
		}
	}
}

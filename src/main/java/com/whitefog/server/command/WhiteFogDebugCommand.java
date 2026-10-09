package com.whitefog.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.whitefog.darkness.LightDiagnostics;
import com.whitefog.darkness.shelter.ShelterSnapshot;
import com.whitefog.WhiteFog;
import com.whitefog.WhiteFogAttachments;
import com.whitefog.breaking.BreakTimerService;
import com.whitefog.station.RecoveryService;
import com.whitefog.darkness.EternalNightWorld;
import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Отладочная команда {@code /whitefog debug [player]} — только для dev-среды.
 *
 * <p>Печатает серверное состояние выбранного игрока и <b>не изменяет данные</b>:
 * используется {@link WhiteFogAttachments#peek(ServerPlayer)}, который не создаёт состояние,
 * если его ещё нет. Если состояния нет, показываются безопасные значения по умолчанию (только в выводе).</p>
 *
 * <p>Регистрируется в {@link com.whitefog.server.WhiteFogServer} только при
 * {@code FabricLoader.isDevelopmentEnvironment()}.</p>
 */
public final class WhiteFogDebugCommand {
	private WhiteFogDebugCommand() {
	}

	/** Регистрирует команду в диспетчере (сигнатура Fabric CommandRegistrationCallback). */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("whitefog")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("debug")
						.executes(WhiteFogDebugCommand::printSelf)
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> printTarget(ctx, EntityArgument.getPlayer(ctx, "player"))))));
	}

	private static int printSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return print(ctx.getSource(), ctx.getSource().getPlayerOrException());
	}

	private static int printTarget(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
		return print(ctx.getSource(), target);
	}

	private static int print(CommandSourceStack source, ServerPlayer target) {
		String name = target.getName().getString();

		LightDiagnostics.Snapshot d = LightDiagnostics.capture(source.getServer(), target);
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("White Fog [" + name + "] подробная диагностика:");
		lines.add("  тик=" + d.serverTick() + " ноги=" + target.blockPosition()
				+ " измерение=" + target.level().dimension().identifier());
		lines.add("  текущее: глаз=" + LightDiagnostics.pos(d.current().eye()) + " blockLight="
				+ d.current().vanillaBlockLight() + " effective=" + d.current().effectiveBlockLight()
				+ " portable=" + d.current().portableEmission() + " небо=" + d.current().canSeeSky());
		String sample = d.exposure().sampled()
				? ("sample: глаз=" + LightDiagnostics.pos(d.exposure().sampleEye()) + " vanilla="
						+ d.exposure().vanillaBlockLight() + " effective=" + d.exposure().effectiveBlockLight()
						+ " portable=" + d.exposure().portableEmission() + " небо=" + d.exposure().canSeeSky()
						+ " exposure=" + d.exposure().exposureBefore() + "->" + d.exposure().exposureAfter()
						+ " (delta=" + d.exposure().exposureDelta() + ") tick=" + d.exposure().sampleTick()
						+ " elapsed=" + (d.serverTick() - d.exposure().sampleTick()))
				: "sample: NO_SAMPLE (серверный sample ещё не выполнен)";
		lines.add("  " + sample + " safeTicks=" + d.state().getSafeLightTicks()
				+ " condition=" + d.state().getCondition());
		lines.add("  persistent: " + d.state().describe(d.serverTick()));
		lines.add("  укрытие=" + (d.shelter() == null ? "нет игрока/диагностики" : d.shelter().snapshot().valid())
				+ " reason=" + (d.shelter() == null ? "NO_PLAYER" : LightDiagnostics.shelterReason(d.shelter().snapshot().reason()))
				+ " cache=" + (d.cachePresent() ? "есть age=" + d.shelter().cacheAge()
						+ " checkedAt=" + d.shelter().cacheEntry().checkedAt() : "нет/stale="
						+ (d.shelter() != null && d.shelter().cacheStale())));
		if (d.shelter() != null) {
			ShelterSnapshot s = d.shelter().snapshot();
			lines.add("  interior bbox=" + s.interiorMin() + ".." + s.interiorMax()
					+ " size=" + s.interior().size() + " dependencies=" + s.dependencies().size());
		}
		lines.add("  Darkness=" + d.darknessActive() + " remaining=" + d.darknessRemaining()
				+ " speedRestriction=" + d.speedRestricted());
		lines.add("  offhand=" + hand(d.offhand()) + " mainhand=" + hand(d.mainhand()));
		lines.add("  nearest=" + (d.nearest().isEmpty() ? "нет" : sourceInfo(d.nearest().get())));
		lines.add("  lifecycle/cache: shelterCache=" + (d.cachePresent() ? "active" : "empty")
				+ ", exposureSampled=" + d.exposure().sampled());
		BreakTimerService.Diagnostic breakState = BreakTimerService.diagnostics(target);
		lines.add("  break: active=" + breakState.active() + " target=" + breakState.pos() + " category="
				+ breakState.category() + " tool=" + breakState.tool() + " timer=" + breakState.elapsedTicks()
				+ "/" + breakState.requiredTicks());
		RecoveryService.Diagnostic recovery = RecoveryService.diagnostics(target);
		lines.add("  recovery: active=" + recovery.active() + " target=" + recovery.target() + " progress="
				+ recovery.elapsedTicks() + "/" + com.whitefog.WhiteFogConfig.RECOVERY_FLAT_STONE_TICKS
				+ " cobblestone=" + recovery.cobblestonePresent() + "/" + recovery.cobblestoneRequired());
		LightSourceService.RuntimeDiagnostic lightJobs = LightSourceService.diagnostics(target);
		lines.add("  light/refuel: active=" + lightJobs.refuelActive() + " target=" + lightJobs.refuelPos()
				+ " elapsed=" + lightJobs.refuelElapsed() + " menuOpen=" + lightJobs.menuOpen()
				+ " menuTarget=" + lightJobs.menuPos() + " menuRevision=" + lightJobs.menuStatus());
		EternalNightWorld.Diagnostic night = EternalNightWorld.diagnostics(source.getServer());
		lines.add("  station/look: " + LightDiagnostics.lookedStation(target));
		lines.add("  eternal-night: advanceTime=" + night.advanceTime() + " clock=" + night.clock()
				+ " totalTicks=" + night.totalTicks() + " timeOfDay=" + night.timeOfDay());
		lines.add("  subsystems: crafting=locked break=read-only-snapshot recovery=read-only-snapshot"
				+ " light/menu=read-only eternal-night=server-enforced");
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal(line), false);
			WhiteFog.LOGGER.info("WHITEFOG_DEBUG player={} {}", target.getStringUUID(), line);
		}
		return 1;
	}

	private static String hand(LightDiagnostics.HandFuel h) {
		return h.item() + " x" + h.count() + " remaining=" + h.remaining() + " lit=" + h.lit() + " burning=" + h.burning();
	}

	private static String sourceInfo(com.whitefog.darkness.light.LightSourceService.Snapshot s) {
		return "pos=" + s.pos() + " remaining=" + s.remaining() + " lit=" + s.lit() + " revision=" + s.revision();
	}
}

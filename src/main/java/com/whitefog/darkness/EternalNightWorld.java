package com.whitefog.darkness;

import com.whitefog.WhiteFog;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Optional;

/**
 * Вечная ночь в Overworld (этап 1.5).
 *
 * <p>Minecraft 26.2 заменил {@code dayTime} на систему WorldClock
 * ({@code net.minecraft.world.clock.ServerClockManager}, {@code DimensionType#defaultClock()}).
 * Проверено по деобфусцированному jar 26.2: {@code ServerClockManager#tick()} читает глобальное
 * правило {@code GameRules.ADVANCE_TIME} (бывшее {@code doDaylightCycle}) и двигает clock только
 * если оно {@code true}; {@code /time set} вызывает {@code ServerClockManager#setTotalTicks}.
 * Источник логики — реальный код Minecraft 26.2 ({@code ServerClockManager}, {@code TimeCommand}),
 * прочитанный через javap; удалённый open-source поиск недоступен (нет сети).</p>
 *
 * <p>Действие: на старте сервера и перед каждым тиком Overworld-уровня выставляем
 * {@code ADVANCE_TIME=false} (дневная симуляция не идёт) и удерживаем время суток 18000
 * (полночь). Внешнее изменение (например, {@code /time set day}) откатывается на следующем
 * тике уровня — обработчик команд не переписывается, права оператора сохраняются.
 * Nether/End не получают фиктивного солнца.</p>
 */
public final class EternalNightWorld {
	public record Diagnostic(boolean advanceTime, long totalTicks, long timeOfDay, String clock) { }
	private static boolean registered = false;
	private static boolean logged = false;

	private EternalNightWorld() {
	}

	public static Diagnostic diagnostics(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) return new Diagnostic(Boolean.TRUE.equals(server.getGameRules().get(GameRules.ADVANCE_TIME)), -1L, -1L, "none");
		Optional<Holder<WorldClock>> clock = overworld.dimensionType().defaultClock();
		if (clock.isEmpty()) return new Diagnostic(Boolean.TRUE.equals(server.getGameRules().get(GameRules.ADVANCE_TIME)), -1L, -1L, "none");
		long ticks = server.clockManager().getTotalTicks(clock.get());
		return new Diagnostic(Boolean.TRUE.equals(server.getGameRules().get(GameRules.ADVANCE_TIME)), ticks,
				Math.floorMod(ticks, DarknessConfig.TICKS_PER_DAY), clock.get().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown"));
	}

	/** Регистрирует lifecycle/tick-хуки вечной ночи. Идемпотентна. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		// Старт сервера: выставить сразу.
		ServerLifecycleEvents.SERVER_STARTED.register(EternalNightWorld::onServerStarted);
		// Перед каждым тиком уровня: восстановить при внешнем изменении (26.2: START_LEVEL_TICK).
		ServerTickEvents.START_LEVEL_TICK.register(EternalNightWorld::onStartLevelTick);
	}

	private static void onServerStarted(MinecraftServer server) {
		try {
			applyEternalNight(server);
			if (!logged) {
				logged = true;
				WhiteFog.LOGGER.info("White Fog: eternal night enabled (advance_time=false, day_time={})",
						DarknessConfig.ETERNAL_NIGHT_DAY_TIME);
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: failed to enable eternal night on server start", e);
		}
	}

	private static void onStartLevelTick(ServerLevel level) {
		try {
			if (!Level.OVERWORLD.equals(level.dimension())) {
				return;
			}
			MinecraftServer server = level.getServer();
			if (server != null) {
				applyEternalNight(server);
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: failed to enforce eternal night on level tick", e);
		}
	}

	/** Отключает дневную симуляцию и удерживает время суток 18000 в Overworld. */
	private static void applyEternalNight(MinecraftServer server) {
		// Глобальные правила сервера (getGlobalGameRules() в 26.2 помечен @Deprecated и просто
		// делегирует в overworld().getGameRules(); используем не-deprecated доступ).
		GameRules rules = server.getGameRules();
		if (Boolean.TRUE.equals(rules.get(GameRules.ADVANCE_TIME))) {
			rules.set(GameRules.ADVANCE_TIME, false, server);
		}

		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		Optional<Holder<WorldClock>> defaultClock = overworld.dimensionType().defaultClock();
		if (defaultClock.isEmpty()) {
			return;
		}
		ServerClockManager clockManager = server.clockManager();
		Holder<WorldClock> clock = defaultClock.get();
		long totalTicks = clockManager.getTotalTicks(clock);
		if (Math.floorMod(totalTicks, DarknessConfig.TICKS_PER_DAY) != DarknessConfig.ETERNAL_NIGHT_DAY_TIME) {
			clockManager.setTotalTicks(clock, DarknessConfig.ETERNAL_NIGHT_DAY_TIME);
		}
	}
}

package com.whitefog.tests.darkness;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sandbox-модель серверного сервиса {@code com.whitefog.darkness.LightExposureService} (этап 1.5).
 *
 * <p>Моделирует: guard повторного серверного тика, накопление remainder 0..19, один sample каждые
 * 20 тиков, применение результата к {@link StateModel}, набор именованных modifier'ов (без
 * накопления), решение о синхронизации (min 5 тиков, heartbeat 100), сброс runtime на
 * disconnect/join. Без Minecraft.</p>
 */
public final class ExposureServiceModel {
	public static final int SAMPLE_INTERVAL = 20;
	public static final int SNAPSHOT_MIN_INTERVAL = 5;
	public static final int SNAPSHOT_HEARTBEAT = 100;
	public static final String SPEED_MODIFIER_ID = "white_fog:darkness_slow";

	public final StateModel state;

	// Контекст клетки глаза (адаптеры).
	public boolean alive = true;
	public boolean exempt = false;
	public boolean loaded = true;
	public int blockLight = 0;
	public boolean canSeeSky = false;
	public boolean shelter = false;
	public boolean victorySafe = false;

	// Runtime.
	public long lastProcessedTick = Long.MIN_VALUE;
	public int sampleCount = 0;
	public int skippedUnloadedSamples = 0;
	public int blockLightLastSample = 0;
	public boolean hasSampled = false;
	public boolean lastSpeedRestricted = false;
	/** Именованные modifier'ы: keyed by id — повторное применение НЕ создаёт второй записи. */
	public final Map<String, String> modifiers = new LinkedHashMap<>();

	// Синхронизация.
	public boolean hasSent = false;
	public long lastSyncedRevision = -1L;
	public long lastSyncTick = Long.MIN_VALUE;
	public int snapshotCount = 0;
	public int lastSentBlockLight = 0;
	public boolean lastSentShelter = false;
	public boolean lastSentSpeedRestricted = false;

	public ExposureServiceModel(StateModel state) {
		this.state = state;
	}

	/** Один серверный тик. Повтор с тем же номером тика не даёт второго sample. */
	public void tick(long now) {
		if (lastProcessedTick == now) {
			return;
		}
		lastProcessedTick = now;

		if (!alive || exempt) {
			modifiers.remove(SPEED_MODIFIER_ID);
			lastSpeedRestricted = false;
			maybeSync(now);
			return;
		}

		int remainder = state.sampleRemainderTicks + 1;
		if (remainder >= SAMPLE_INTERVAL) {
			remainder -= SAMPLE_INTERVAL;
			state.sampleRemainderTicks = remainder;
			sample();
		} else {
			state.sampleRemainderTicks = remainder;
		}

		maybeSync(now);
	}

	private void sample() {
		// Eye position не загружена — sample пропускается без догоняющего расчёта.
		if (!loaded) {
			skippedUnloadedSamples++;
			return;
		}
		sampleCount++;
		ExposurePolicy.Result result = ExposurePolicy.evaluate(
				new ExposurePolicy.Input(blockLight, canSeeSky, shelter, true, false, victorySafe),
				state.lightExposure, state.safeLightTicks, state.darknessConditionMilli);

		if (result.exposure() != state.lightExposure) {
			state.lightExposure = result.exposure();
			state.darknessRevision++;
		}
		if (result.safeTicks() != state.safeLightTicks) {
			state.safeLightTicks = result.safeTicks();
			state.darknessRevision++;
		}
		if (result.conditionMilli() != state.darknessConditionMilli) {
			state.darknessConditionMilli = result.conditionMilli();
			state.condition = result.conditionMilli() / 1000.0F;
			state.darknessRevision++;
		}

		blockLightLastSample = blockLight;
		hasSampled = true;
		lastSpeedRestricted = result.speedRestricted();
		if (result.speedRestricted()) {
			modifiers.put(SPEED_MODIFIER_ID, SPEED_MODIFIER_ID);
		} else {
			modifiers.remove(SPEED_MODIFIER_ID);
		}
	}

	private void maybeSync(long now) {
		long since = hasSent ? now - lastSyncTick : Long.MAX_VALUE;
		boolean changed = !hasSent
				|| state.darknessRevision != lastSyncedRevision
				|| blockLightLastSample != lastSentBlockLight
				|| shelter != lastSentShelter
				|| lastSpeedRestricted != lastSentSpeedRestricted;
		boolean periodic = since >= SNAPSHOT_HEARTBEAT;
		if (!changed && !periodic) {
			return;
		}
		if (hasSent && since < SNAPSHOT_MIN_INTERVAL) {
			return;
		}
		sendSnapshot(now);
	}

	private void sendSnapshot(long now) {
		snapshotCount++;
		hasSent = true;
		lastSyncedRevision = state.darknessRevision;
		lastSyncTick = now;
		lastSentBlockLight = blockLightLastSample;
		lastSentShelter = shelter;
		lastSentSpeedRestricted = lastSpeedRestricted;
	}

	/** Аналог onPlayerJoined/onPlayerRespawned: свежая runtime-сессия + немедленный снимок. */
	public void onJoin(long now) {
		lastProcessedTick = now;
		hasSent = false;
		lastSyncTick = Long.MIN_VALUE;
		sendSnapshot(now);
	}

	/** Аналог disconnect: снять modifier/cache, attachment сохраняется. */
	public void onDisconnect() {
		modifiers.remove(SPEED_MODIFIER_ID);
		lastSpeedRestricted = false;
		lastProcessedTick = Long.MIN_VALUE;
		hasSent = false;
		lastSyncedRevision = -1L;
		lastSyncTick = Long.MIN_VALUE;
	}

	/** Прогоняет {@code ticks} последовательных серверных тиков начиная с {@code fromTick}. */
	public void run(long fromTick, int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(fromTick + i);
		}
	}
}

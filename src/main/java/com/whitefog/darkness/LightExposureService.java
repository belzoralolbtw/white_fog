package com.whitefog.darkness;

import com.whitefog.WhiteFog;
import com.whitefog.WhiteFogAttachments;
import com.whitefog.darkness.light.PortableLightService;
import com.whitefog.darkness.shelter.ShelterProvider;
import com.whitefog.network.DarknessSnapshotPayload;
import com.whitefog.state.PlayerSurvivalState;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.LightLayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный сервис вечной ночи и воздействия тьмы (этап 1.5).
 *
 * <p>Вызывается из единственного {@code END_SERVER_TICK} в
 * {@link com.whitefog.server.WhiteFogServer} (второй player tick НЕ регистрируется).</p>
 *
 * <p>Адаптеры Minecraft: чтение block light/неба в клетке глаза; применение vanilla Darkness;
 * именованный modifier скорости; обычный подтверждённый death path; S2C-снимок тьмы.
 * Формула вынесена в чистую {@link LightExposurePolicy}.</p>
 *
 * <p>Ссылки (прочитано/сверено): Fabric API {@code fabric-lifecycle-events-v1}
 * ({@code ServerTickEvents}/{@code ServerLifecycleEvents}) и реальные классы Minecraft 26.2
 * ({@code BlockAndLightGetter#getBrightness/canSeeSky}, {@code MobEffects.DARKNESS},
 * {@code Attributes.MOVEMENT_SPEED}, {@code LivingEntity#kill(ServerLevel)}) — проверено по
 * деобфусцированным jar 26.2. Удалённый open-source поиск в этой среде недоступен (нет сети),
 * поэтому источником политики служит утверждённый контракт ROADMAP_STEPS.md (этап 1.5).</p>
 */
public final class LightExposureService {
	public record Diagnostic(boolean sampled, long sampleTick, BlockPos sampleEye, int vanillaBlockLight,
			int effectiveBlockLight, int portableEmission, boolean canSeeSky, int exposureBefore,
			int exposureAfter, int exposureDelta, boolean shelter, boolean speedRestricted) { }
	/** Runtime-сессии (не сохраняются): guard повторного tick, последний sample, состояние синхронизации. */
	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_THRESHOLD_STATE = new ConcurrentHashMap<>();

	private static boolean registered = false;

	private LightExposureService() {
	}

	/** Явная точка регистрации сервиса (логирование). Идемпотентна. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WhiteFog.LOGGER.info("White Fog: darkness light-exposure service registered (stage 1.5, server-authoritative)");
	}

	// ------------------------------------------------------------------
	// Серверный тик
	// ------------------------------------------------------------------

	/** Обрабатывает всех online-игроков за один серверный тик. */
	public static void tickAll(MinecraftServer server) {
		try {
			List<ServerPlayer> players = server.getPlayerList().getPlayers();
			for (ServerPlayer player : players) {
				try {
					tickPlayer(server, player);
				} catch (RuntimeException e) {
					WhiteFog.LOGGER.error("White Fog: darkness tick failed for player {}",
							player.getStringUUID(), e);
				}
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: darkness tick-all failed", e);
		}
	}

	/** Обрабатывает одного игрока. Повторный вызов с тем же номером тика не начисляет второй sample. */
	private static void tickPlayer(MinecraftServer server, ServerPlayer player) {
		Session session = session(player);
		int now = server.getTickCount();
		if (session.lastProcessedTick == now) {
			return; // защита от повторной обработки того же серверного тика
		}
		session.lastProcessedTick = now;

		PlayerSurvivalState state = WhiteFogAttachments.getOrCreate(player);

		// Мёртвый игрок: sample не выполняется; runtime-modifier снимается.
		if (!player.isAlive()) {
			ShelterProvider.clear(player.getUUID());
			session.lastShelter = false;
			session.hasSampled = false;
			removeSpeedModifier(player);
			session.lastSpeedRestricted = false;
			maybeSync(server, player, state, session);
			return;
		}

		// Creative/spectator не накапливают remainder и не получают штрафов; шкалы приостановлены.
		if (player.isCreative() || player.isSpectator()) {
			removeSpeedModifier(player);
			session.lastSpeedRestricted = false;
			maybeSync(server, player, state, session);
			return;
		}

		// Online survival/adventure: копим remainder, каждые 20 фактических тиков — один sample.
		int remainder = state.getSampleRemainderTicks() + 1;
		if (remainder >= DarknessConfig.SAMPLE_INTERVAL_TICKS) {
			remainder -= DarknessConfig.SAMPLE_INTERVAL_TICKS;
			state.setSampleRemainderTicks(remainder);
			sample(server, player, state, session);
		} else {
			state.setSampleRemainderTicks(remainder);
		}

		// Запрет спринта на сервере действует каждый тик, пока штраф активен.
		if (session.lastSpeedRestricted && player.isSprinting()) {
			player.setSprinting(false);
		}

		maybeSync(server, player, state, session);
	}

	/** Один sample: пересчёт чистой политикой и применение адаптеров Minecraft. */
	private static void sample(MinecraftServer server, ServerPlayer player, PlayerSurvivalState state,
			Session session) {
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		// Если клетка глаза не загружена — sample пропускается без догоняющего расчёта.
		if (!player.level().isLoaded(eye)) {
			ShelterProvider.clear(player.getUUID());
			session.lastShelter = false;
			session.hasSampled = false;
			return;
		}

		int blockLight = player.level().getBrightness(LightLayer.BLOCK, eye);
		// Адаптер переносного света (этап поверх 1.6): вход exposure поднимается до
		// max(vanilla blockLight, portable emission) ДО неизменной формулы политики.
		// Это НЕ радиус и НЕ сложение.
		int portableEmission = PortableLightService.portableEmission(player);
		int effectiveBlockLight = PortableLightService.effectiveBlockLight(player, blockLight);
		boolean canSeeSky = player.level().canSeeSky(eye);
		boolean shelter = isSheltered(player);
		boolean victorySafe = isVictorySafe(player); // до готовности победы возвращает false
		int previousExposure = state.getLightExposure();

		LightExposurePolicy.Input input = new LightExposurePolicy.Input(
				effectiveBlockLight, canSeeSky, shelter, true, false, victorySafe);
		LightExposurePolicy.Result result = LightExposurePolicy.evaluate(input,
				state.getLightExposure(), state.getSafeLightTicks(), state.getDarknessConditionMilli());

		if (result.exposure() != state.getLightExposure()) {
			state.setLightExposure(result.exposure());
		}
		if (result.safeLightTicks() != state.getSafeLightTicks()) {
			state.setSafeLightTicks(result.safeLightTicks());
		}
		if (result.conditionMilli() != state.getDarknessConditionMilli()) {
			state.setDarknessConditionMilli(result.conditionMilli());
		}

		session.lastVanillaBlockLight = blockLight;
		session.lastEffectiveBlockLight = effectiveBlockLight;
		session.lastPortableEmission = portableEmission;
		session.lastBlockLight = effectiveBlockLight;
		session.lastSampleEye = eye;
		session.previousExposure = previousExposure;
		session.exposureAfter = result.exposure();
		session.exposureDelta = result.exposure() - previousExposure;
		session.lastSampleTick = server.getTickCount();
		session.lastCanSeeSky = canSeeSky;
		session.lastShelter = shelter;
		session.lastSpeedRestricted = result.speedRestricted();
		session.hasSampled = true;
		String thresholdState = (result.exposure() >= DarknessConfig.DARK_EFFECT_EXPOSURE_THRESHOLD) + ":"
				+ (result.exposure() >= DarknessConfig.SPEED_RESTRICT_EXPOSURE_THRESHOLD) + ":"
				+ (result.exposure() >= DarknessConfig.CONDITION_DAMAGE_EXPOSURE_THRESHOLD);
		String previousThreshold = LAST_THRESHOLD_STATE.put(player.getUUID(), thresholdState);
		if (!thresholdState.equals(previousThreshold)) WhiteFog.LOGGER.info(
				"WHITEFOG_EXPOSURE_THRESHOLDS player={} dimension={} pos={} exposure={} darkness={} slow={} conditionDrain={}",
				player.getStringUUID(), player.level().dimension().identifier(), player.blockPosition(), result.exposure(),
				result.exposure() >= DarknessConfig.DARK_EFFECT_EXPOSURE_THRESHOLD,
				result.exposure() >= DarknessConfig.SPEED_RESTRICT_EXPOSURE_THRESHOLD,
				result.exposure() >= DarknessConfig.CONDITION_DAMAGE_EXPOSURE_THRESHOLD);

		refreshDarknessEffect(player, result.exposure());
		applySpeedRestriction(player, result.speedRestricted());
		if (result.speedRestricted() && player.isSprinting()) {
			player.setSprinting(false);
		}

		// Истощение тьмой завершает жизнь обычным подтверждённым death path.
		if (result.deathRequired()) {
			player.kill(player.level());
		}
	}

	/** Loaded-only stage 1.7 adapter, evaluated before the unchanged exposure policy. */
	private static boolean isSheltered(ServerPlayer player) {
		return ShelterProvider.isSheltered(player);
	}

	/** Адаптер победы. До готовности механики победы всегда {@code false}. */
	private static boolean isVictorySafe(ServerPlayer player) {
		return false;
	}

	// ------------------------------------------------------------------
	// Эффект Darkness
	// ------------------------------------------------------------------

	/**
	 * Накладывает/обновляет vanilla Darkness при exposure >= порога.
	 * Чужой эффект не удаляется: при длительности больше порога обновления не трогаем, иначе
	 * обновляем ДЛИТЕЛЬНОСТЬЮ {@link DarknessConfig#DARK_EFFECT_DURATION_TICKS}, которая заметно
	 * больше blend-advance Darkness ({@link DarknessConfig#DARK_BLEND_ADVANCE_TICKS}). Благодаря
	 * этому фактор смешивания эффекта не «проваливается» между обновлениями, и ванильная
	 * пульсация затемнения не перезапускается (см. DarknessConfig).
	 *
	 * <p>Мод НИКОГДА не снимает эффект сам (в т.ч. чужой/слепоту): при уходе exposure ниже порога
	 * мы просто перестаём обновлять длительность, и наш эффект истекает естественно.</p>
	 */
	private static void refreshDarknessEffect(ServerPlayer player, int exposure) {
		if (exposure < DarknessConfig.DARK_EFFECT_EXPOSURE_THRESHOLD) {
			return;
		}
		MobEffectInstance existing = player.getEffect(MobEffects.DARKNESS);
		if (existing == null || (!existing.isInfiniteDuration()
				&& existing.getDuration() <= DarknessConfig.DARK_EFFECT_REFRESH_REMAINING_TICKS)) {
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DarknessConfig.DARK_EFFECT_DURATION_TICKS,
					DarknessConfig.DARK_EFFECT_AMPLIFIER, false, false));
		}
	}

	// ------------------------------------------------------------------
	// Штраф скорости
	// ------------------------------------------------------------------

	/** Единственный именованный modifier {@code white_fog:darkness_slow} (без накопления). */
	private static void applySpeedRestriction(ServerPlayer player, boolean restricted) {
		AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute == null) {
			return;
		}
		Identifier id = speedModifierId();
		if (restricted) {
			AttributeModifier existing = attribute.getModifier(id);
			if (existing == null || existing.amount() != DarknessConfig.SPEED_MODIFIER_AMOUNT
					|| existing.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
				attribute.addOrUpdateTransientModifier(new AttributeModifier(id, DarknessConfig.SPEED_MODIFIER_AMOUNT,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			attribute.removeModifier(id);
		}
	}

	/** Снимает только свой modifier скорости. */
	private static void removeSpeedModifier(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute != null) {
			attribute.removeModifier(speedModifierId());
		}
	}

	private static Identifier speedModifierId() {
		return WhiteFog.id(DarknessConfig.SPEED_MODIFIER_PATH);
	}

	// ------------------------------------------------------------------
	// Синхронизация снимка
	// ------------------------------------------------------------------

	/**
	 * Решает, нужно ли отправить снимок: по изменению ревизии/полей либо по heartbeat.
	 * Минимальный интервал между снимками соблюдается; heartbeat его перекрывает.
	 */
	private static void maybeSync(MinecraftServer server, ServerPlayer player, PlayerSurvivalState state,
			Session session) {
		int now = server.getTickCount();
		long since = session.hasSent ? (long) now - session.lastSyncTick : Long.MAX_VALUE;
		boolean changed = !session.hasSent
				|| state.getDarknessRevision() != session.lastSyncedRevision
				|| session.lastBlockLight != session.lastSentBlockLight
				|| session.lastShelter != session.lastSentShelter
				|| session.lastSpeedRestricted != session.lastSentSpeedRestricted;
		boolean periodic = since >= DarknessConfig.SNAPSHOT_HEARTBEAT_TICKS;
		if (!changed && !periodic) {
			return;
		}
		if (session.hasSent && since < DarknessConfig.SNAPSHOT_MIN_INTERVAL_TICKS) {
			return;
		}
		sendSnapshot(server, player, state, session);
	}

	/** Немедленный снимок (вход/reспавн/смена измерения), без ограничения минимального интервала. */
	public static void sendNow(MinecraftServer server, ServerPlayer player) {
		Session session = session(player);
		PlayerSurvivalState state = WhiteFogAttachments.getOrCreate(player);
		// После load/join guard равен now: offline-время не симулируется.
		session.lastProcessedTick = server.getTickCount();
		sendSnapshot(server, player, state, session);
	}

	/** Принудительно отправляет снимок и обновляет bookkeeping синхронизации. */
	private static void sendSnapshot(MinecraftServer server, ServerPlayer player, PlayerSurvivalState state,
			Session session) {
		if (!ServerPlayNetworking.canSend(player, DarknessSnapshotPayload.TYPE)) {
			return;
		}
		int blockLight = session.hasSampled ? session.lastBlockLight : peekBlockLight(player);
		boolean shelter = session.hasSampled && session.lastShelter;
		boolean speedRestricted = state.getLightExposure() >= DarknessConfig.SPEED_RESTRICT_EXPOSURE_THRESHOLD;
		int now = server.getTickCount();

		ServerPlayNetworking.send(player, new DarknessSnapshotPayload(
				state.getDarknessRevision(),
				blockLight,
				state.getLightExposure(),
				state.getSafeLightTicks(),
				state.getDarknessConditionMilli(),
				shelter,
				speedRestricted));

		session.hasSent = true;
		session.lastSyncedRevision = state.getDarknessRevision();
		session.lastSyncTick = now;
		session.lastSentBlockLight = blockLight;
		session.lastSentShelter = shelter;
		session.lastSentSpeedRestricted = speedRestricted;
	}

	/** Текущий block light клетки глаза (для немедленного снимка до первого sample). */
	private static int peekBlockLight(ServerPlayer player) {
		try {
			BlockPos eye = BlockPos.containing(player.getEyePosition());
			if (!player.level().isLoaded(eye)) {
				return 0;
			}
			return player.level().getBrightness(LightLayer.BLOCK, eye);
		} catch (RuntimeException e) {
			return 0;
		}
	}

	// ------------------------------------------------------------------
	// Жизненный цикл игрока
	// ------------------------------------------------------------------

	/** Вход игрока: свежая runtime-сессия + немедленный снимок. */
	public static void onPlayerJoined(MinecraftServer server, ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		LAST_THRESHOLD_STATE.remove(player.getUUID());
		sendNow(server, player);
	}

	/** Респавн: сбрасываем runtime (attachment перенесён через copyOnDeath) + снимок. */
	public static void onPlayerRespawned(MinecraftServer server, ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		sendNow(server, player);
	}

	/** Смена измерения: старый sample не является состоянием нового измерения. */
	public static void onPlayerChangeLevel(MinecraftServer server, ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		sendNow(server, player);
	}

	/** Отключение: снимаем runtime modifier/cache; attachment сохраняется. */
	public static void clear(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		try {
			removeSpeedModifier(player);
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: failed to clear darkness runtime for player {}",
					player.getStringUUID(), e);
		}
	}

	private static Session session(ServerPlayer player) {
		return SESSIONS.computeIfAbsent(player.getUUID(), key -> new Session());
	}

	/** Runtime-сессия игрока (не сохраняется). */
	private static final class Session {
		private long lastProcessedTick = Long.MIN_VALUE;
		private boolean hasSampled = false;
		private int lastBlockLight = 0;
		private boolean lastShelter = false;
		private boolean lastSpeedRestricted = false;
		private boolean hasSent = false;
		private long lastSyncedRevision = -1L;
		private long lastSyncTick = Long.MIN_VALUE;
		private int lastSentBlockLight = 0;
		private boolean lastSentShelter = false;
		private boolean lastSentSpeedRestricted = false;
		private int lastVanillaBlockLight = 0;
		private int lastEffectiveBlockLight = 0;
		private int lastPortableEmission = 0;
		private int previousExposure = 0;
		private int exposureAfter = 0;
		private int exposureDelta = 0;
		private long lastSampleTick = Long.MIN_VALUE;
		private boolean lastCanSeeSky = false;
		private BlockPos lastSampleEye = null;
	}

	/** Read-only runtime snapshot for the dev diagnostic command. */
	public static Diagnostic diagnostics(MinecraftServer server, ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || !session.hasSampled) {
			return new Diagnostic(false, Long.MIN_VALUE, null, 0, 0, 0, false, 0, 0, 0, false, false);
		}
		return new Diagnostic(true, session.lastSampleTick, session.lastSampleEye, session.lastVanillaBlockLight,
				session.lastEffectiveBlockLight, session.lastPortableEmission, session.lastCanSeeSky,
				session.previousExposure, session.exposureAfter, session.exposureDelta,
				session.lastShelter, session.lastSpeedRestricted);
	}
}

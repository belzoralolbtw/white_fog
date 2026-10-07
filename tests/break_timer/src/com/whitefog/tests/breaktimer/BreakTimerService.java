package com.whitefog.tests.breaktimer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ядро прототипа: серверно-авторитетный lifecycle таймера разрушения.
 *
 * <p>Моделирует обработку START/STOP/ABORT и серверный tick, как это будет сделано
 * миксином в {@code ServerPlayerGameMode#handleBlockBreakAction} в MC 26.2.
 * Клиентский progress bar здесь ни на что не влияет: завершает только сервер,
 * когда прошло требуемое число тиков.</p>
 *
 * <p>Это ЧИСТАЯ ЛОГИКА. Она не доказывает runtime-поведение Minecraft — для этого
 * нужна сборка + запуск игры (см. AGENTS.md «этап 1.3»).</p>
 */
public final class BreakTimerService {

	/** Итог обработки действия. */
	public enum Outcome {
		STARTED,
		ALREADY_ACTIVE,
		START_REFUSED,
		PREMATURE_STOP,
		COMMITTED,
		ABORTED,
		NO_SESSION,
		TICK_OK,
		TICK_ABORTED,
		SUPPRESSED
	}

	/** Причина, по которой активная сессия была прервана во время tick/stop. */
	public enum AbortReason {
		OK,
		CHUNK_UNLOADED,
		DIMENSION_CHANGED,
		TOOL_CHANGED,
		BLOCK_CHANGED,
		OUT_OF_RANGE,
		HOT,
		FILLED
	}

	/** Счётчики для self-теста и dev-диагностики. */
	public static final class Stats {
		public int starts;
		public int refusals;
		public int messages;
		public int premature;
		public int aborts;
		public int commits;
		public int suppressed;
		public int tickAborts;

		@Override
		public String toString() {
			return "starts=" + starts + " refusals=" + refusals + " messages=" + messages
					+ " premature=" + premature + " aborts=" + aborts + " commits=" + commits
					+ " suppressed=" + suppressed + " tickAborts=" + tickAborts;
		}
	}

	private final WorldModel world;
	private final BreakPolicy policy;
	private final boolean creativeBypass;

	private final Map<String, BreakSession> sessionsByPlayer = new HashMap<>();
	private final Set<BlockKey> committed = new HashSet<>();
	private final Map<String, Long> lastMessageTick = new HashMap<>();
	private final Stats stats = new Stats();
	private AbortReason lastAbortReason = AbortReason.OK;

	public BreakTimerService(WorldModel world, BreakPolicy policy, boolean creativeBypass) {
		this.world = world;
		this.policy = policy;
		this.creativeBypass = creativeBypass;
	}

	public Stats stats() {
		return stats;
	}

	public AbortReason lastAbortReason() {
		return lastAbortReason;
	}

	public boolean hasSession(String playerUuid) {
		return sessionsByPlayer.containsKey(playerUuid);
	}

	// ------------------------------------------------------------------
	// Протокол
	// ------------------------------------------------------------------

	public Outcome start(PlayerRef player, ToolStack tool, BlockKey key, long now) {
		BlockKind kind = world.blockKind(key);
		if (kind == null || world.isAir(key)) {
			// Блок уже разрушен/не существует — стартовать нечего.
			return Outcome.START_REFUSED;
		}

		ToolKind toolKind = policy.classify(tool);

		// Наполненная станция/ёмкость: отказ до опустошения (динамическая проверка, а не по категории).
		if (world.isFilled(key) && kind.isStation()) {
			return refuse(player, now);
		}

		BreakPolicy.Decision decision = policy.evaluateStart(kind, tool, toolKind);
		if (decision != BreakPolicy.Decision.ALLOW) {
			return refuse(player, now);
		}

		if (player.creative && creativeBypass) {
			// Creative: таймер = 0 (мгновенный commit), но ВСЕ проверки категории/инструмента уже пройдены.
			return commitNow(key, kind, toolKind);
		}

		BreakSession previous = sessionsByPlayer.get(player.uuid);
		if (previous != null) {
			if (previous.key().equals(key)) {
				return Outcome.ALREADY_ACTIVE;
			}
			// Начата новая цель — старая сессия отменяется (как в ванильном gamemode).
			sessionsByPlayer.remove(player.uuid);
			stats.aborts++;
		}

		int required = policy.requiredTicks(kind, toolKind);
		sessionsByPlayer.put(player.uuid,
				new BreakSession(player.uuid, key, tool, toolKind, kind, now, required));
		stats.starts++;
		return Outcome.STARTED;
	}

	/** STOP: коммит только если серверный таймер уже дозрел. Преждевременный STOP сессию НЕ снимает. */
	public Outcome stop(PlayerRef player, ToolStack tool, BlockKey key, long now) {
		BreakSession session = sessionsByPlayer.get(player.uuid);
		if (session == null) {
			return Outcome.NO_SESSION;
		}
		AbortReason reason = validate(session, player, tool);
		if (reason != AbortReason.OK) {
			sessionsByPlayer.remove(player.uuid);
			lastAbortReason = reason;
			stats.tickAborts++;
			return Outcome.TICK_ABORTED;
		}
		if (!session.ready(now)) {
			// Преждевременный STOP (клиентское предсказание в 26.2): сессию СОХРАНЯЕМ — серверный tick доведёт.
			stats.premature++;
			return Outcome.PREMATURE_STOP;
		}
		return commit(session);
	}

	public Outcome abort(PlayerRef player, BlockKey key, long now) {
		BreakSession session = sessionsByPlayer.get(player.uuid);
		if (session == null) {
			return Outcome.NO_SESSION;
		}
		sessionsByPlayer.remove(player.uuid);
		stats.aborts++;
		return Outcome.ABORTED;
	}

	/**
	 * Серверный tick активной сессии: валидирует цель и, если серверный таймер дозрел,
	 * завершает разрушение БЕЗ участия клиента.
	 */
	public Outcome tick(PlayerRef player, ToolStack tool, BlockKey key, long now) {
		BreakSession session = sessionsByPlayer.get(player.uuid);
		if (session == null) {
			return Outcome.NO_SESSION;
		}
		AbortReason reason = validate(session, player, tool);
		if (reason != AbortReason.OK) {
			sessionsByPlayer.remove(player.uuid);
			lastAbortReason = reason;
			stats.tickAborts++;
			return Outcome.TICK_ABORTED;
		}
		if (session.ready(now)) {
			return commit(session);
		}
		return Outcome.TICK_OK;
	}

	// ------------------------------------------------------------------
	// Внутреннее
	// ------------------------------------------------------------------

	private AbortReason validate(BreakSession session, PlayerRef player, ToolStack tool) {
		if (!world.isChunkLoaded(session.key())) {
			return AbortReason.CHUNK_UNLOADED;
		}
		if (!player.dimension.equals(session.key().dimension())) {
			return AbortReason.DIMENSION_CHANGED;
		}
		if (!tool.equals(session.tool())) {
			return AbortReason.TOOL_CHANGED;
		}
		BlockKind nowKind = world.blockKind(session.key());
		if (nowKind != session.startKind()) {
			return AbortReason.BLOCK_CHANGED;
		}
		if (!player.withinReach(session.key())) {
			return AbortReason.OUT_OF_RANGE;
		}
		if (nowKind != null && nowKind.isHot()) {
			return AbortReason.HOT;
		}
		// Наполненность перепроверяется динамически: содержимое добавляется без смены BlockState.
		if (nowKind != null && nowKind.isStation() && world.isFilled(session.key())) {
			return AbortReason.FILLED;
		}
		return AbortReason.OK;
	}

	private Outcome commit(BreakSession session) {
		sessionsByPlayer.remove(session.playerUuid());
		return commitNow(session.key(), session.startKind(), session.toolKind());
	}

	/** Пакетный доступ — используется white-box self-тестом для проверки защиты «ровно один раз». */
	Outcome commitNow(BlockKey key, BlockKind kind, ToolKind toolKind) {
		// Дедупликация: блок уже изменён (нами или вторым игроком) → без второго изменения и дропа.
		if (world.isAir(key) || committed.contains(key)) {
			stats.suppressed++;
			return Outcome.SUPPRESSED;
		}
		// Последний defensive guard: наполненную станцию не снимаем (содержимое не теряется).
		if (kind.isStation() && world.isFilled(key)) {
			stats.suppressed++;
			return Outcome.SUPPRESSED;
		}
		int drops = policy.dropsOnCommit(kind, toolKind);
		world.setAir(key);
		if (drops > 0) {
			world.spawnDrop(key, drops);
		}
		committed.add(key);
		stats.commits++;
		return Outcome.COMMITTED;
	}

	private Outcome refuse(PlayerRef player, long now) {
		stats.refusals++;
		long last = lastMessageTick.getOrDefault(player.uuid, Long.MIN_VALUE);
		if (last == Long.MIN_VALUE || now - last >= policy.messageCooldownTicks()) {
			stats.messages++;
			lastMessageTick.put(player.uuid, now);
		}
		return Outcome.START_REFUSED;
	}
}

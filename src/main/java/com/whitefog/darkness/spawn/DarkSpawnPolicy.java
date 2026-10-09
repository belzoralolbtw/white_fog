package com.whitefog.darkness.spawn;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Чистая (без Minecraft-API) политика этапа 1.8 «Опасность тёмных участков».
 *
 * <p>Здесь собраны все константы контракта, детерминированный seed/RNG, выбор target-игрока,
 * проверки границ/света/cap и выбор кандидата на спавн. Вся работа с миром выполняется через
 * {@link CandidateView}, который реализует серверный адаптер {@link DarkSpawnService}; благодаря
 * этому алгоритм полностью покрывается sandbox-тестом без запуска игры.</p>
 *
 * <p>Ссылки (прочитано, адаптировано, не скопировано):</p>
 * <ul>
 *     <li>{@code Glitchfiend/SereneSeasons} — {@code common/.../season/RandomUpdateHandler.java}
 *         (обход loaded ticking chunks через {@code level.getChunkSource().chunkMap}
 *         {@code .forEachBlockTickingChunk(...)} и проверка близости игрока);</li>
 *     <li>{@code AlexModGuy/AlexsMobs} / {@code bonsaistudi0s/Creeper-Overhaul} —
 *         {@code Monster.isDarkEnoughToSpawn(...)} как образец «тёмного» условия спавна
 *         (здесь заменён строгим block-light predicate по контракту);</li>
 *     <li>{@code MinecraftForge/ForgeEventFactory#checkSpawnPosition}
 *         ({@code mob.checkSpawnRules(...) && mob.checkSpawnObstruction(...)}) — каноничная пара
 *         валидации позиции моба, применённая в серверном адаптере.</li>
 * </ul>
 */
public final class DarkSpawnPolicy {
	// ------------------------------------------------------------------
	// Константы контракта (этап 1.8)
	// ------------------------------------------------------------------

	/** Период обработки в loaded server ticks (100 т = 5 с). */
	public static final int PERIOD_TICKS = 100;
	/** Ровно 8 кандидатов X/Z на попытку. */
	public static final int CANDIDATES_PER_PERIOD = 8;
	/** Нижняя граница горизонтального радиуса игрока от центра chunk (включительно), блоки. */
	public static final int PLAYER_MIN_DISTANCE = 24;
	/** Верхняя граница горизонтального радиуса игрока от центра chunk (включительно), блоки. */
	public static final int PLAYER_MAX_DISTANCE = 32;
	/** Порог exposure, с которого игрок считается уязвимым. */
	public static final int EXPOSURE_THRESHOLD = 75;
	/** Граница roll: первый {@code nextInt(ROLL_BOUND)} задаёт бросок. */
	public static final int ROLL_BOUND = 100;
	/** Значения roll 0..ROLL_PERMIT_MAX разрешают попытку (25 из 100). */
	public static final int ROLL_PERMIT_MAX = 24;
	/** Максимум помеченных (DARK_AMBIENT) мобов на chunk. */
	public static final int CHUNK_CAP = 2;
	/** Максимум помеченных (DARK_AMBIENT) мобов на измерение. */
	public static final int DIMENSION_CAP = 12;
	/** Вертикальное окно поиска пола вокруг ног target-игрока (±8 блоков). */
	public static final int VERTICAL_WINDOW = 8;
	/** Максимальный block light у ног кандидата (sky channel не учитывается). */
	public static final int BLOCK_LIGHT_MAX = 4;
	/** Множитель seed по X (значение из контракта этапа 1.8). */
	public static final long SEED_X_MULTIPLIER = 341_873_128_712L;
	/** Множитель seed по Z (значение из контракта этапа 1.8). */
	public static final long SEED_Z_MULTIPLIER = 132_897_987_541L;

	/** Тип vanilla-моба, выпадающий по {@code successfulSpawns % 4}. Новые EntityType не создаются. */
	public enum MobKind { ZOMBIE, SKELETON, SPIDER, CREEPER }

	/** Вход policy по игроку: только то, что нужно для выбора target и дистанций. */
	public record PlayerInput(UUID uuid, double x, double z, int feetY, boolean alive,
			boolean survivalOrAdventure, int exposure) { }

	/** Результат детерминированной попытки: seed, roll и 8 упакованных X/Z offset (x<<4|z). */
	public record Attempt(long seed, int roll, int[] candidateOffsets) {
		/** X offset i-го кандидата (0..15). */
		public int offsetX(int i) {
			return (candidateOffsets[i] >>> 4) & 0xF;
		}
		/** Z offset i-го кандидата (0..15). */
		public int offsetZ(int i) {
			return candidateOffsets[i] & 0xF;
		}
		/** Число кандидатов (всегда {@link #CANDIDATES_PER_PERIOD}). */
		public int candidateCount() {
			return candidateOffsets.length;
		}
	}

	/** Выбранная позиция спавна (координаты блока ног). */
	public record Candidate(int blockX, int blockY, int blockZ) { }

	/**
	 * Read-only взгляд policy на мир. Серверный адаптер читает реальные loaded-блоки,
	 * sandbox подставляет детерминированную заглушку.
	 */
	public interface CandidateView {
		/** Загружен ли chunk колонки {@code blockX/blockZ}. */
		boolean isLoadedColumn(int blockX, int blockZ);
		/** Полный solid floor под ногами кандидата. */
		boolean hasFullSolidFloor(int blockX, int blockY, int blockZ);
		/** Свободны ли воздух y+1/y+2, нет жидкости, полная AABB моба не пересекает коллизии. */
		boolean isAirAndClear(int blockX, int blockY, int blockZ, MobKind kind);
		/** Серверный block light у ног кандидата (0..15). */
		int blockLight(int blockX, int blockY, int blockZ);
		/** Находится ли позиция ног внутри укрытия (read-only shelter detector). */
		boolean isSheltered(int blockX, int blockY, int blockZ);
	}

	private DarkSpawnPolicy() {
	}

	// ------------------------------------------------------------------
	// Детерминированный RNG
	// ------------------------------------------------------------------

	/** Seed контракта: {@code worldSeed XOR chunkX*341873128712 XOR chunkZ*132897987541 XOR floor(gameTime/100)}. */
	public static long seed(long worldSeed, int chunkX, int chunkZ, long gameTimeBucket) {
		return worldSeed ^ (chunkX * SEED_X_MULTIPLIER) ^ (chunkZ * SEED_Z_MULTIPLIER) ^ gameTimeBucket;
	}

	/** Bucket игрового времени: {@code floor(gameTime / PERIOD_TICKS)} (целочисленное деление без переполнения). */
	public static long gameTimeBucket(long gameTime) {
		return Math.floorDiv(gameTime, (long) PERIOD_TICKS);
	}

	/**
	 * Детерминированная попытка из seed: первый {@code nextInt(100)} — roll, затем ровно 8 пар X/Z 0..15.
	 * Один и тот же seed всегда даёт один и тот же roll и список offset.
	 */
	public static Attempt attemptFor(long seed) {
		Random rng = new Random(seed);
		int roll = rng.nextInt(ROLL_BOUND);
		int[] offsets = new int[CANDIDATES_PER_PERIOD];
		for (int i = 0; i < offsets.length; i++) {
			int x = rng.nextInt(16);
			int z = rng.nextInt(16);
			offsets[i] = (x << 4) | z;
		}
		return new Attempt(seed, roll, offsets);
	}

	/** Разрешает ли попытку бросок: значения 0..24 (25 из 100). */
	public static boolean permitsAttemptForRoll(int roll) {
		return roll >= 0 && roll <= ROLL_PERMIT_MAX;
	}

	// ------------------------------------------------------------------
	// Входы policy
	// ------------------------------------------------------------------

	/** Достигнут ли порог уязвимости. */
	public static boolean meetsExposure(int exposure) {
		return exposure >= EXPOSURE_THRESHOLD;
	}

	/** Горизонтальная дистанция игрока от центра chunk в разрешённом радиусе [24;32]. */
	public static boolean inPlayerRadius(double distance) {
		return distance >= PLAYER_MIN_DISTANCE && distance <= PLAYER_MAX_DISTANCE;
	}

	/** Не меньше ли 24 блоков по горизонтали от кандидата до любого живого игрока. */
	public static boolean farEnoughFromAnyPlayer(double candidateX, double candidateZ, List<PlayerInput> alivePlayers) {
		double minSq = (double) PLAYER_MIN_DISTANCE * (double) PLAYER_MIN_DISTANCE;
		for (PlayerInput player : alivePlayers) {
			if (player == null || !player.alive()) {
				continue;
			}
			double dx = candidateX - player.x();
			double dz = candidateZ - player.z();
			if (dx * dx + dz * dz < minSq) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Target player: ближайший к центру chunk среди alive survival/adventure с exposure>=порога,
	 * находящихся в радиусе 24..32. При равной дистанции — лексикографически меньший UUID.
	 */
	public static PlayerInput nearestEligiblePlayer(List<PlayerInput> players, double centerX, double centerZ) {
		PlayerInput best = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (PlayerInput player : players) {
			if (player == null || !player.alive() || !player.survivalOrAdventure()
					|| !meetsExposure(player.exposure())) {
				continue;
			}
			double dx = player.x() - centerX;
			double dz = player.z() - centerZ;
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (!inPlayerRadius(distance)) {
				continue;
			}
			if (best == null || distance < bestDistance
					|| (distance == bestDistance && compareUuid(player.uuid(), best.uuid()) < 0)) {
				best = player;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static int compareUuid(UUID a, UUID b) {
		if (a == null) {
			return b == null ? 0 : 1;
		}
		if (b == null) {
			return -1;
		}
		return a.compareTo(b);
	}

	/** У ног кандидата серверный block light<=4 (sky channel в predicate не участвует). */
	public static boolean blockLightOk(int blockLight) {
		return blockLight <= BLOCK_LIGHT_MAX;
	}

	/** Cap только DARK_AMBIENT: <2 на chunk и <12 на измерение. Не зависит от числа игроков. */
	public static boolean capAllows(int chunkMarked, int dimensionMarked) {
		return chunkMarked < CHUNK_CAP && dimensionMarked < DIMENSION_CAP;
	}

	/** Тип моба по числу успешных спавнов: 0→zombie, 1→skeleton, 2→spider, 3→creeper. */
	public static MobKind kindFor(int successfulSpawns) {
		MobKind[] values = MobKind.values();
		return values[Math.floorMod(successfulSpawns, values.length)];
	}

	// ------------------------------------------------------------------
	// Выбор кандидата
	// ------------------------------------------------------------------

	/**
	 * Первый валидный кандидат из 8 X/Z offset. Для каждого offset перебирается Y пола top-down
	 * в окне {@code targetFeetY±8}; ноги моба находятся на {@code floorY+1}, воздух — на
	 * {@code floorY+1..floorY+2}. Невалидная позиция не прекращает перебор. Проверка укрытия —
	 * последняя (самая дорогая), после дешёвых проверок пола/воздуха/света/дистанции.
	 *
	 * @return позиция блока ног ({@code floorY+1}) или {@code null}, если валидного кандидата нет.
	 */
	public static Candidate selectCandidate(MobKind kind, int targetFeetY, int chunkMinBlockX, int chunkMinBlockZ,
			Attempt attempt, List<PlayerInput> alivePlayers, CandidateView view) {
		int floorTop = targetFeetY + VERTICAL_WINDOW;
		int floorBottom = targetFeetY - VERTICAL_WINDOW;
		for (int i = 0; i < attempt.candidateCount(); i++) {
			int x = chunkMinBlockX + attempt.offsetX(i);
			int z = chunkMinBlockZ + attempt.offsetZ(i);
			if (!view.isLoadedColumn(x, z)) {
				continue;
			}
			// Горизонтальная дистанция до любого живого игрока >=24; от Y не зависит — проверяется один раз.
			if (!farEnoughFromAnyPlayer(x + 0.5D, z + 0.5D, alivePlayers)) {
				continue;
			}
			for (int floorY = floorTop; floorY >= floorBottom; floorY--) {
				int feetY = floorY + 1;
				if (!view.hasFullSolidFloor(x, floorY, z)) {
					continue;
				}
				if (!view.isAirAndClear(x, feetY, z, kind)) {
					continue;
				}
				if (!blockLightOk(view.blockLight(x, feetY, z))) {
					continue;
				}
				if (view.isSheltered(x, feetY, z)) {
					continue;
				}
				return new Candidate(x, feetY, z);
			}
		}
		return null;
	}
}

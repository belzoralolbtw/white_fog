package com.whitefog.darkness.spawn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Чистое (без Minecraft-API) состояние подсистемы тёмного спавна (этап 1.8).
 *
 * <p>Один экземпляр на измерение. Хранит три независимых слоя:</p>
 * <ul>
 *     <li><b>Persistent</b> — счётчик попыток на chunk {@code {lastAttemptGameTime, successfulSpawns}},
 *         сериализуется через {@link DarkSpawnStore};</li>
 *     <li><b>Runtime индекс</b> — loaded помеченные (DARK_AMBIENT) сущности по chunk их спавна
 *         и суммарно по измерению; восстанавливается из persistent marker событиями загрузки сущностей;</li>
 *     <li><b>Reservations</b> — счётчик зарезервированных слотов внутри одного тика, чтобы два
 *         кандидата/чанка в одном проходе не перешли cap. Сбрасывается каждый тик обработки.</li>
 * </ul>
 *
 * <p>Метод {@code onDirty} вызывается только при фактическом изменении persistent-счётчиков
 * (для {@code SavedData#setDirty}); runtime индекс и reservations его не трогают.</p>
 */
public final class DarkMobState {
	/** Значение origin для marker: отличает модовых мобов от vanilla. */
	public static final String DARK_AMBIENT_ORIGIN = "DARK_AMBIENT";

	/** Persistent-счётчик попыток на chunk. */
	public record Attempt(long lastAttemptGameTime, int successfulSpawns) { }

	/** Модовый marker, прикрепляемый к созданной vanilla-сущности. */
	public record Marker(String origin, long spawnChunk, UUID spawnUuid) { }

	private final Runnable onDirty;
	/** chunkKey (ChunkPos.pack) → попытки. Единственные сериализуемые данные. */
	private final Map<Long, Attempt> attempts = new HashMap<>();
	/** chunkKey спавна → UUID loaded помеченных мобов. */
	private final Map<Long, Set<UUID>> loadedByChunk = new HashMap<>();
	/** Все loaded помеченные UUID измерения (для cap по измерению). */
	private final Set<UUID> loadedAll = new HashSet<>();
	/** chunkKey → число reservations в текущем проходе. */
	private final Map<Long, Integer> reservedByChunk = new HashMap<>();
	/** reservations по измерению в текущем проходе. */
	private int reservedDimension;

	/** Состояние без персистенции (sandbox/pure-контекст). */
	public DarkMobState() {
		this(() -> { });
	}

	/** @param onDirty колбэк изменения persistent-данных (передаётся в {@code SavedData#setDirty}). */
	public DarkMobState(Runnable onDirty) {
		this.onDirty = onDirty == null ? () -> { } : onDirty;
	}

	// ------------------------------------------------------------------
	// Persistent: попытки на chunk
	// ------------------------------------------------------------------

	/** Текущая запись попыток chunk или {@code null}. */
	public Attempt attempt(long chunkKey) {
		return attempts.get(chunkKey);
	}

	/** Число успешных спавнов chunk (тип моба). */
	public int successfulSpawns(long chunkKey) {
		Attempt attempt = attempts.get(chunkKey);
		return attempt == null ? 0 : attempt.successfulSpawns();
	}

	/** true, если для этого chunk+gameTime bucket попытка уже создавалась (повтор не выполняется). */
	public boolean bucketProcessed(long chunkKey, long bucket) {
		Attempt attempt = attempts.get(chunkKey);
		return attempt != null && attempt.lastAttemptGameTime() == bucket;
	}

	/** Фиксирует bucket попытки, не меняя счётчик успешных спавнов. */
	public void markAttempt(long chunkKey, long bucket) {
		Attempt existing = attempts.get(chunkKey);
		int success = existing == null ? 0 : existing.successfulSpawns();
		attempts.put(chunkKey, new Attempt(bucket, success));
		onDirty.run();
	}

	/** Увеличивает счётчик успешных спавнов chunk (вызывается ТОЛЬКО после успешного addFreshEntity). */
	public void recordSuccess(long chunkKey) {
		Attempt existing = attempts.get(chunkKey);
		long lastAttempt = existing == null ? 0L : existing.lastAttemptGameTime();
		int success = (existing == null ? 0 : existing.successfulSpawns()) + 1;
		attempts.put(chunkKey, new Attempt(lastAttempt, success));
		onDirty.run();
	}

	/** Копия persistent-карты попыток (для сериализации). */
	public Map<Long, Attempt> attemptsView() {
		return Map.copyOf(attempts);
	}

	/** Восстанавливает persistent-карту (загрузка из {@link DarkSpawnStore}); onDirty не вызывается. */
	public void restoreAttempts(Map<Long, Attempt> loaded) {
		attempts.clear();
		if (loaded != null) {
			attempts.putAll(loaded);
		}
	}

	// ------------------------------------------------------------------
	// Runtime индекс loaded помеченных мобов
	// ------------------------------------------------------------------

	/** Добавляет loaded помеченного моба (идемпотентно по UUID). */
	public void addLoaded(long spawnChunk, UUID uuid) {
		if (uuid == null) {
			return;
		}
		if (loadedAll.add(uuid)) {
			loadedByChunk.computeIfAbsent(spawnChunk, key -> new HashSet<>()).add(uuid);
		}
	}

	/** Убирает моба из loaded индекса (remove/unload). */
	public void removeLoaded(long spawnChunk, UUID uuid) {
		if (uuid == null) {
			return;
		}
		if (loadedAll.remove(uuid)) {
			Set<UUID> set = loadedByChunk.get(spawnChunk);
			if (set != null) {
				set.remove(uuid);
				if (set.isEmpty()) {
					loadedByChunk.remove(spawnChunk);
				}
			}
		}
	}

	/** Число loaded помеченных мобов, чей chunk спавна совпадает с заданным. */
	public int loadedChunkCount(long spawnChunk) {
		Set<UUID> set = loadedByChunk.get(spawnChunk);
		return set == null ? 0 : set.size();
	}

	/** Число loaded помеченных мобов во всём измерении. */
	public int loadedDimensionCount() {
		return loadedAll.size();
	}

	/** Полная очистка runtime (никогда не трогает persistent attempts). */
	public void clearRuntime() {
		loadedByChunk.clear();
		loadedAll.clear();
		reservedByChunk.clear();
		reservedDimension = 0;
	}

	// ------------------------------------------------------------------
	// Reservations внутри тика
	// ------------------------------------------------------------------

	/** Сбрасывает reservations (вызывается в начале обработки измерения). */
	public void resetReservations() {
		reservedByChunk.clear();
		reservedDimension = 0;
	}

	/**
	 * Резервирует один слот, если с учётом уже зарезервированных cap ещё не достигнут.
	 * Повторная проверка перед {@code addFreshEntity} не даёт двум кандидатам перейти cap.
	 */
	public boolean tryReserve(long chunkKey) {
		int chunkCount = loadedChunkCount(chunkKey) + reservedByChunk.getOrDefault(chunkKey, 0);
		int dimensionCount = loadedDimensionCount() + reservedDimension;
		if (!DarkSpawnPolicy.capAllows(chunkCount, dimensionCount)) {
			return false;
		}
		reservedByChunk.merge(chunkKey, 1, Integer::sum);
		reservedDimension++;
		return true;
	}

	/** Снимает reservation (при неуспешном spawn). */
	public void releaseReservation(long chunkKey) {
		Integer current = reservedByChunk.get(chunkKey);
		if (current != null) {
			if (current <= 1) {
				reservedByChunk.remove(chunkKey);
			} else {
				reservedByChunk.put(chunkKey, current - 1);
			}
		}
		if (reservedDimension > 0) {
			reservedDimension--;
		}
	}

	/** Зарезервировано слотов в chunk (диагностика/тест). */
	public int reservedChunk(long chunkKey) {
		return reservedByChunk.getOrDefault(chunkKey, 0);
	}

	/** Зарезервировано слотов в измерении (диагностика/тест). */
	public int reservedDimension() {
		return reservedDimension;
	}
}

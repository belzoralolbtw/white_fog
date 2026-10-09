package com.whitefog.darkness.spawn;

import com.mojang.serialization.Codec;

import com.whitefog.WhiteFog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent-хранилище попыток тёмного спавна, по одному на измерение (этап 1.8).
 *
 * <p>Хранит только {@code chunkKey → {lastAttemptGameTime, successfulSpawns}} — ровно persistent-часть
 * {@link DarkMobState}. Runtime-индекс loaded мобов и reservations не сохраняются (индекс
 * восстанавливается из marker на сущностях событиями Fabric {@code ServerEntityEvents}).</p>
 *
 * <p>Сохранение/загрузка — через {@link SavedDataType} в {@code SavedDataStorage} измерения;
 * состояние переживает save/load и unload/load чанка. Формат повторяет приём {@code LightSourceStore}
 * (CompoundTag-компаунд и {@code DataFixTypes.LEVEL}).</p>
 */
public final class DarkSpawnStore extends SavedData {

	/** Идентификатор SavedData: {@code white_fog:dark_spawn}. */
	public static final String STORE_ID = "dark_spawn";

	/** Codec хранилища: NBT-компаунд ↔ объект. */
	public static final Codec<DarkSpawnStore> CODEC =
			CompoundTag.CODEC.xmap(DarkSpawnStore::fromTag, DarkSpawnStore::toTag);

	/** Тип SavedData для per-dimension {@code computeIfAbsent}. */
	public static final SavedDataType<DarkSpawnStore> TYPE = new SavedDataType<>(
			WhiteFog.id(STORE_ID), DarkSpawnStore::new, CODEC, DataFixTypes.LEVEL);

	private static final String TAG_SCHEMA = "schema";
	private static final String TAG_ATTEMPTS = "attempts";
	private static final String TAG_CHUNK = "chunk";
	private static final String TAG_LAST_ATTEMPT = "last_attempt";
	private static final String TAG_SUCCESSFUL = "successful";
	private static final int SCHEMA_VERSION = 1;

	/** Чистое состояние измерения (persistent + runtime). */
	private final DarkMobState state;

	/** Пустое хранилище (свежий мир). */
	public DarkSpawnStore() {
		this.state = new DarkMobState(this::setDirty);
	}

	/** Состояние измерения. */
	public DarkMobState state() {
		return this.state;
	}

	// ------------------------------------------------------------------
	// Сериализация
	// ------------------------------------------------------------------

	/** Читает хранилище из NBT. Отсутствующие/битые записи безопасно пропускаются. */
	public static DarkSpawnStore fromTag(CompoundTag tag) {
		DarkSpawnStore store = new DarkSpawnStore();
		Map<Long, DarkMobState.Attempt> attempts = new HashMap<>();
		ListTag list = tag.getListOrEmpty(TAG_ATTEMPTS);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			long chunk = entry.getLongOr(TAG_CHUNK, Long.MIN_VALUE);
			if (chunk == Long.MIN_VALUE) {
				continue;
			}
			long lastAttempt = entry.getLongOr(TAG_LAST_ATTEMPT, 0L);
			int successful = Math.max(0, entry.getIntOr(TAG_SUCCESSFUL, 0));
			attempts.put(chunk, new DarkMobState.Attempt(lastAttempt, successful));
		}
		store.state.restoreAttempts(attempts);
		return store;
	}

	/** Записывает хранилище в NBT. */
	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt(TAG_SCHEMA, SCHEMA_VERSION);
		ListTag list = new ListTag();
		for (Map.Entry<Long, DarkMobState.Attempt> e : this.state.attemptsView().entrySet()) {
			CompoundTag entry = new CompoundTag();
			entry.putLong(TAG_CHUNK, e.getKey());
			entry.putLong(TAG_LAST_ATTEMPT, e.getValue().lastAttemptGameTime());
			entry.putInt(TAG_SUCCESSFUL, Math.max(0, e.getValue().successfulSpawns()));
			list.addAndUnwrap(entry);
		}
		tag.put(TAG_ATTEMPTS, list);
		return tag;
	}
}

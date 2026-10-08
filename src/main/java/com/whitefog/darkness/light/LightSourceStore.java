package com.whitefog.darkness.light;

import com.mojang.serialization.Codec;

import com.whitefog.WhiteFog;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent-хранилище гаснущих источников света, по одному на измерение (этап 1.6).
 *
 * <p>Запись по позиции: {@code {sourceUuid, expectedBlockId, remainingTicks, revision, managedByPost}}.
 * BlockEntity к каждому факелу НЕ добавляется. Минимальные defaults: {@code remainingTicks=0},
 * {@code revision=0}, {@code managedByPost=false}.</p>
 *
 * <p>Отдельно хранится persistent-флаг инициализированных чанков: генерационный бонус выдаётся
 * ровно один раз на чанк (ленивый scan при первом доступе), а установленный игроком источник
 * получает запись сразу и потому бонуса не получает. Полного скана мира каждый тик нет — тикаются
 * только записи этого хранилища.</p>
 *
 * <p>Сохранение/загрузка — через {@link SavedDataType} в {@code SavedDataStorage} измерения;
 * состояние переживает save/load и unload/load чанка.</p>
 */
public final class LightSourceStore extends SavedData {

	/** Codec хранилища: NBT-компаунд ↔ объект. */
	public static final Codec<LightSourceStore> CODEC =
			CompoundTag.CODEC.xmap(LightSourceStore::fromTag, LightSourceStore::toTag);

	/** Тип SavedData для per-dimension {@code computeIfAbsent}. */
	public static final SavedDataType<LightSourceStore> TYPE = new SavedDataType<>(
			WhiteFog.id(LightConfig.STORE_ID), LightSourceStore::new, CODEC, DataFixTypes.LEVEL);

	private static final String TAG_SCHEMA = "schema";
	private static final String TAG_RECORDS = "records";
	private static final String TAG_CHUNKS = "initialized_chunks";
	private static final String TAG_X = "x";
	private static final String TAG_Y = "y";
	private static final String TAG_Z = "z";
	private static final String TAG_UUID_MOST = "uuid_most";
	private static final String TAG_UUID_LEAST = "uuid_least";
	private static final String TAG_BLOCK = "block";
	private static final String TAG_REMAINING = "remaining";
	private static final String TAG_REVISION = "revision";
	private static final String TAG_MANAGED = "managed_by_post";

	/** Записи источников, ключ — immutable позиция блока. */
	private final Map<BlockPos, Record> records = new HashMap<>();
	/** Long-ключи (ChunkPos.toLong()) чанков, где генерационный scan уже выполнен. */
	private final java.util.Set<Long> initializedChunks = new java.util.HashSet<>();
	/** Версия схемы данных (для будущих миграций). */
	private int schema = LightConfig.STORE_SCHEMA_VERSION;

	/** Пустое хранилище (свежий мир). */
	public LightSourceStore() {
	}

	/** Запись об одном источнике. */
	public record Record(UUID sourceUuid, String expectedBlockId, int remainingTicks, long revision,
			boolean managedByPost) {
		/** Копия с новым остатком и инкрементом ревизии. */
		public Record withFuel(int newRemaining, long newRevision) {
			return new Record(sourceUuid, expectedBlockId, newRemaining, newRevision, managedByPost);
		}

		/** Копия с изменённым признаком managedByPost. */
		public Record withManagedByPost(boolean managed) {
			return new Record(sourceUuid, expectedBlockId, remainingTicks, revision, managed);
		}
	}

	// ------------------------------------------------------------------
	// Доступ к записям
	// ------------------------------------------------------------------

	/** Запись по позиции или {@code null}. */
	public Record get(BlockPos pos) {
		return records.get(pos);
	}

	/** Сохраняет запись (позиция копируется в immutable), помечает данные грязными. */
	public void put(BlockPos pos, Record record) {
		records.put(pos.immutable(), record);
		setDirty();
	}

	/** Удаляет запись и возвращает её (или {@code null}). */
	public Record remove(BlockPos pos) {
		Record removed = records.remove(pos);
		if (removed != null) {
			setDirty();
		}
		return removed;
	}

	/** Все записи (позиция → запись) для тика/поиска. */
	public Collection<Map.Entry<BlockPos, Record>> entries() {
		return new ArrayList<>(records.entrySet());
	}

	/** Количество записей (для диагностики). */
	public int size() {
		return records.size();
	}

	// ------------------------------------------------------------------
	// Флаг инициализации чанка
	// ------------------------------------------------------------------

	/** Помечен ли чанк как уже просканированный (генерационный бонус выдан). */
	public boolean isChunkInitialized(long chunkKey) {
		return initializedChunks.contains(chunkKey);
	}

	/** Помечает чанк просканированным. */
	public void markChunkInitialized(long chunkKey) {
		if (initializedChunks.add(chunkKey)) {
			setDirty();
		}
	}

	// ------------------------------------------------------------------
	// Сериализация
	// ------------------------------------------------------------------

	/** Читает хранилище из NBT. */
	public static LightSourceStore fromTag(CompoundTag tag) {
		LightSourceStore store = new LightSourceStore();
		store.schema = Math.max(1, tag.getIntOr(TAG_SCHEMA, LightConfig.STORE_SCHEMA_VERSION));

		for (int i = 0; i < tag.getListOrEmpty(TAG_RECORDS).size(); i++) {
			CompoundTag entry = tag.getListOrEmpty(TAG_RECORDS).getCompoundOrEmpty(i);
			BlockPos pos = new BlockPos(entry.getIntOr(TAG_X, 0), entry.getIntOr(TAG_Y, 0), entry.getIntOr(TAG_Z, 0));
			UUID uuid = new UUID(entry.getLongOr(TAG_UUID_MOST, 0L), entry.getLongOr(TAG_UUID_LEAST, 0L));
			String block = entry.getStringOr(TAG_BLOCK, "");
			int remaining = Math.max(0, entry.getIntOr(TAG_REMAINING, 0));
			long revision = Math.max(0L, entry.getLongOr(TAG_REVISION, 0L));
			boolean managed = entry.getBooleanOr(TAG_MANAGED, false);
			store.records.put(pos.immutable(), new Record(uuid, block, remaining, revision, managed));
		}

		tag.getLongArray(TAG_CHUNKS).ifPresent(chunks -> {
			for (long key : chunks) {
				store.initializedChunks.add(key);
			}
		});
		return store;
	}

	/** Записывает хранилище в NBT. */
	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt(TAG_SCHEMA, this.schema);

		ListTag list = new ListTag();
		for (Map.Entry<BlockPos, Record> e : this.records.entrySet()) {
			BlockPos pos = e.getKey();
			Record r = e.getValue();
			CompoundTag entry = new CompoundTag();
			entry.putInt(TAG_X, pos.getX());
			entry.putInt(TAG_Y, pos.getY());
			entry.putInt(TAG_Z, pos.getZ());
			entry.putLong(TAG_UUID_MOST, r.sourceUuid() == null ? 0L : r.sourceUuid().getMostSignificantBits());
			entry.putLong(TAG_UUID_LEAST, r.sourceUuid() == null ? 0L : r.sourceUuid().getLeastSignificantBits());
			entry.putString(TAG_BLOCK, r.expectedBlockId() == null ? "" : r.expectedBlockId());
			entry.putInt(TAG_REMAINING, Math.max(0, r.remainingTicks()));
			entry.putLong(TAG_REVISION, Math.max(0L, r.revision()));
			entry.putBoolean(TAG_MANAGED, r.managedByPost());
			list.addAndUnwrap(entry);
		}
		tag.put(TAG_RECORDS, list);

		long[] chunks = new long[this.initializedChunks.size()];
		int i = 0;
		for (long key : this.initializedChunks) {
			chunks[i++] = key;
		}
		tag.putLongArray(TAG_CHUNKS, chunks);
		return tag;
	}

	/** Список записей (для диагностических логов). */
	public List<String> describe() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, Record> e : this.records.entrySet()) {
			Record r = e.getValue();
			out.add(e.getKey().toShortString() + " uuid=" + r.sourceUuid() + " block=" + r.expectedBlockId()
					+ " remaining=" + r.remainingTicks() + " revision=" + r.revision()
					+ " managedByPost=" + r.managedByPost());
		}
		return out;
	}
}

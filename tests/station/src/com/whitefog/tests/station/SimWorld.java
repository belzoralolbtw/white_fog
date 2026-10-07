package com.whitefog.tests.station;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Чистая модель мира для sandbox этапа 1.4 «Плоский камень и камушки».
 *
 * <p><b>Это НЕ Minecraft.</b> Здесь нет {@code BlockState}, {@code ItemStack}, block entity
 * или сети — только логика, которую затем повторяет серверный {@code src}. Модель нужна,
 * чтобы доказать инварианты (однократность, сохранение количества, revision/job guards)
 * до применения в реальном коде (см. AGENTS.md §3).</p>
 *
 * <p>Мир хранит блоки по позиции, список выпавших предметов (переживает unload/load чанка —
 * как настоящий {@code ItemEntity} в выгруженном чанке сохраняется на диск) и счётчики
 * для проверок.</p>
 */
public final class SimWorld {

	/** Ключ позиции блока: измерение + координаты. */
	public record StationKey(String dimension, int x, int y, int z) {
	}

	/** Выпавший предмет (модель {@code ItemEntity}). */
	public static final class Drop {
		public final StationKey pos;
		public final String itemId;
		public final int count;
		public int pickupDelayTicks;

		Drop(StationKey pos, String itemId, int count, int pickupDelayTicks) {
			this.pos = pos;
			this.itemId = itemId;
			this.count = count;
			this.pickupDelayTicks = pickupDelayTicks;
		}
	}

	/** Установленный блок станции (модель block entity у flat_stone; у small_stone данных нет). */
	public static final class Block {
		public final String id;
		/** Ревизия: увеличивается при каждом серверном изменении содержимого/состояния. */
		public long revision;
		/** Владелец активного job (null — job нет). */
		public String jobOwner;
		/** Входной escrow (упрощённо — количество). */
		public int escrow;
		/** Выход (упрощённо — количество). */
		public int output;
		/** Режим job: CRAFT / FORGE. */
		public String mode = "CRAFT";
		/** Прогресс активного job в тиках. */
		public int jobProgress;
		/** Требуемое время активного job в тиках. */
		public int jobRequired;
		/** schemaVersion block entity (у small_stone не используется). */
		public int schemaVersion = 1;

		Block(String id) {
			this.id = id;
		}

		/** Станция занята: есть job либо непустые escrow/output. */
		public boolean isBusy() {
			return this.jobOwner != null || this.escrow > 0 || this.output > 0;
		}
	}

	private final Map<StationKey, Block> blocks = new HashMap<>();
	private final List<Drop> drops = new ArrayList<>();
	private final Set<StationKey> loadedChunks = new HashSet<>();

	/** Сколько раз блок реально менялся (удалён/поставлен) — для проверки однократности. */
	public int blockChanges;
	/** Сколько предметов было выдано в инвентарь игрока. */
	public int givenToInventory;
	/** Сколько предметов выпало отдельными сущностями. */
	public int givenAsDrop;
	/** Сообщения, отправленные игроку (например «Сначала забери материалы и результат»). */
	public final List<String> messages = new ArrayList<>();

	// ------------------------------------------------------------------
	// Блоки
	// ------------------------------------------------------------------

	public Block get(StationKey key) {
		return this.blocks.get(key);
	}

	public boolean has(StationKey key) {
		return this.blocks.containsKey(key);
	}

	public void put(StationKey key, String blockId) {
		this.blocks.put(key, new Block(blockId));
		this.blockChanges++;
	}

	/** Устанавливает уже готовый block entity (для save/load и job-сценариев). */
	public void putBlock(StationKey key, Block block) {
		this.blocks.put(key, block);
		this.blockChanges++;
	}

	/** Удаляет блок без выдачи дропа (однократное изменение). Возвращает удалённый блок. */
	public Block removeRaw(StationKey key) {
		Block removed = this.blocks.remove(key);
		if (removed != null) {
			this.blockChanges++;
		}
		return removed;
	}

	public boolean isLoaded(StationKey key) {
		return this.loadedChunks.contains(chunkKey(key));
	}

	public void loadChunk(StationKey key) {
		this.loadedChunks.add(chunkKey(key));
	}

	public void unloadChunk(StationKey key) {
		this.loadedChunks.remove(chunkKey(key));
	}

	private static StationKey chunkKey(StationKey key) {
		// Модель: чанк 16x16, ключ по (dim, x>>4, z>>4).
		return new StationKey(key.dimension(), key.x() >> 4, 0, key.z() >> 4);
	}

	// ------------------------------------------------------------------
	// Предметы
	// ------------------------------------------------------------------

	public void spawnDrop(StationKey pos, String itemId, int count, int pickupDelayTicks) {
		this.drops.add(new Drop(pos, itemId, count, pickupDelayTicks));
		this.givenAsDrop += count;
	}

	public List<Drop> drops() {
		return this.drops;
	}

	public int totalDrops() {
		return this.givenAsDrop;
	}

	public void clearDrops() {
		this.drops.clear();
	}

	public void message(String text) {
		this.messages.add(text);
	}

	public int blockCount() {
		return this.blocks.size();
	}
}

package com.whitefog.tests.light;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Чистая логическая модель гаснущих источников света (этап 1.6) для sandbox-теста.
 *
 * <p>Повторяет правила {@code LightFuelPolicy}/{@code LightSourceStore}/{@code LightSourceService}
 * БЕЗ Minecraft: ёмкости, добавки, нормализация компонента, ticking loaded+lit, пауза unlit,
 * переполнение, замена источника, split/merge, managedByPost. Это НЕ runtime-proof.</p>
 */
public final class LightModel {
	private LightModel() {
	}

	/** Вид источника. */
	public enum Kind {
		TORCH(12_000, 6_000),
		SOUL_TORCH(8_000, 4_000),
		LANTERN(24_000, 12_000),
		SOUL_LANTERN(16_000, 8_000),
		CAMPFIRE(16_000, 8_000);

		public final int capacity;
		public final int litBonus;

		Kind(int capacity, int litBonus) {
			this.capacity = capacity;
			this.litBonus = litBonus;
		}

		public int generatedBonus(boolean lit) {
			return this == CAMPFIRE && !lit ? 0 : this.litBonus;
		}
	}

	/** Топливо. */
	public enum Fuel {
		COAL, CHARCOAL, STICK, OAK_LOG, SPRUCE_LOG, BIRCH_LOG
	}

	/** Позиция (аналог BlockPos). */
	public record Pos(int x, int y, int z) {
	}

	/** Запись источника. */
	public static final class Record {
		public UUID uuid;
		public String blockId;
		public int remaining;
		public long revision;
		public boolean managedByPost;
		public boolean lit;

		public Record(UUID uuid, String blockId, int remaining, long revision, boolean managedByPost, boolean lit) {
			this.uuid = uuid;
			this.blockId = blockId;
			this.remaining = remaining;
			this.revision = revision;
			this.managedByPost = managedByPost;
			this.lit = lit;
		}
	}

	/** Хранилище (аналог LightSourceStore). */
	public static final class Store {
		public final Map<Pos, Record> records = new HashMap<>();
		public final Set<Long> initializedChunks = new HashSet<>();
	}

	/** Нормализация значения компонента. */
	public static int normalize(Integer componentValue, int capacity) {
		if (componentValue == null || componentValue < 0) {
			return 0;
		}
		return Math.min(componentValue, capacity);
	}

	/** Проверка переполнения. */
	public static boolean fits(int remaining, int addition, int capacity) {
		return addition > 0 && (long) remaining + addition <= capacity;
	}

	/** Добавка за один предмет. */
	public static int addition(Kind kind, Fuel fuel) {
		boolean coal = fuel == Fuel.COAL || fuel == Fuel.CHARCOAL;
		return switch (kind) {
			case TORCH -> coal ? 12_000 : -1;
			case SOUL_TORCH -> coal ? 8_000 : -1;
			case LANTERN -> coal ? 24_000 : -1;
			case SOUL_LANTERN -> coal ? 16_000 : -1;
			case CAMPFIRE -> switch (fuel) {
				case COAL, CHARCOAL -> 16_000;
				case STICK -> 2_000;
				case OAK_LOG, SPRUCE_LOG, BIRCH_LOG -> 8_000;
			};
		};
	}

	/** Инициализация сгенерированного источника (bonus ровно один раз). */
	public static Record initializeGenerated(Kind kind, boolean lit, String blockId) {
		return new Record(UUID.randomUUID(), blockId, kind.generatedBonus(lit), 1L, false, lit);
	}

	/**
	 * Установка предмета: charged count&gt;1 отклоняется (возврат null). Иначе запись без бонуса.
	 */
	public static Record place(Kind kind, String blockId, Integer componentValue, int count) {
		int remaining = normalize(componentValue, kind.capacity);
		if (remaining > 0 && count > 1) {
			return null;
		}
		return new Record(UUID.randomUUID(), blockId, remaining, 1L, false, remaining > 0);
	}

	/** Один тик источника: loaded+lit → remaining--; 1→0 гасит. */
	public static void tick(Store store, Pos pos, boolean loaded, boolean isItemEntity) {
		if (!loaded || isItemEntity) {
			return; // unloaded/ItemEntity не тикаются
		}
		Record r = store.records.get(pos);
		if (r == null || !r.lit) {
			return; // unlit с запасом не тратит топливо
		}
		r.remaining--;
		if (r.remaining <= 0) {
			r.remaining = 0;
			r.lit = false;
			r.revision++;
		}
	}

	/** Refuel: сначала считаем дельту, потом проверяем переполнение. */
	public enum RefuelResult { OK, FULL }

	public static RefuelResult refuel(Record r, Kind kind, Fuel fuel) {
		int add = addition(kind, fuel);
		if (add <= 0 || !fits(r.remaining, add, kind.capacity)) {
			return RefuelResult.FULL;
		}
		r.remaining += add;
		r.lit = true;
		r.revision++;
		return RefuelResult.OK;
	}

	/** Зажечь: remaining&gt;0 и unlit → lit без расхода. */
	public static boolean light(Record r) {
		if (!r.lit && r.remaining > 0) {
			r.lit = true;
			r.revision++;
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------
	// nearest (аналог LightSourceService.nearest)
	// ------------------------------------------------------------------

	/** Снимок ближайшего источника (аналог {@code LightSourceService.Snapshot}). */
	public record Snapshot(Pos pos, Record record, boolean lit) {
	}

	/**
	 * Аналог {@code LightSourceService.nearest}: ближайший loaded валидный managed источник в радиусе
	 * с LOS (расстояние между центрами; tie-break по x/y/z).
	 *
	 * <p><b>Ключевое отличие фикса этапа 1.6:</b> {@code lit} НЕ фильтруется — погасший источник
	 * (в т.ч. только что поставленный пустой {@code remaining=0}) должен быть обнаружим для Work Panel.
	 * Правила loaded/block id/дистанция/LOS/tie-break сохранены.</p>
	 */
	public static Snapshot nearest(Store store, Pos eye, double radius, Set<Pos> loaded,
			Map<Pos, String> worldBlockIds, Set<Pos> hasLos) {
		double radiusSq = radius * radius;
		Snapshot best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (Map.Entry<Pos, Record> entry : store.records.entrySet()) {
			Pos pos = entry.getKey();
			Record record = entry.getValue();
			if (!loaded.contains(pos)) {
				continue; // unload: не находим
			}
			String worldId = worldBlockIds.get(pos);
			if (worldId == null || !worldId.equals(record.blockId)) {
				continue; // блок заменён/ожидаемый id не совпадает
			}
			double distSq = distanceSq(eye, pos);
			if (distSq > radiusSq) {
				continue; // вне радиуса
			}
			if (!hasLos.contains(pos)) {
				continue; // нет прямой видимости
			}
			// lit НЕ проверяется сознательно (см. Javadoc).
			boolean better;
			if (best == null || distSq < bestDistSq - 1.0E-6D) {
				better = true;
			} else if (Math.abs(distSq - bestDistSq) <= 1.0E-6D) {
				better = tieBetter(pos, best.pos());
			} else {
				better = false;
			}
			if (better) {
				bestDistSq = distSq;
				best = new Snapshot(pos, record, record.lit);
			}
		}
		return best;
	}

	private static double distanceSq(Pos a, Pos b) {
		double dx = a.x() - b.x();
		double dy = a.y() - b.y();
		double dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private static boolean tieBetter(Pos candidate, Pos best) {
		if (candidate.x() != best.x()) {
			return candidate.x() < best.x();
		}
		if (candidate.y() != best.y()) {
			return candidate.y() < best.y();
		}
		return candidate.z() < best.z();
	}
}

package com.whitefog.tests.lightfix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Чистая модель записи источника в {@code LightSourceStore} при ручной установке.
 *
 * <p>Доказывает два дефекта старого порядка и их устранение новым:</p>
 * <ol>
 *   <li><b>block-id mismatch.</b> У {@code StandingAndWallBlockItem} {@code getStateForPlacement}
 *       вызывается несколько раз; последним в {@code Pending} мог оказаться другой вариант
 *       (например, стоячий torch), хотя поставлен wall_torch. Старый commit писал «на глаз»
 *       ожидаемый id, и {@code tickLevel} удалял запись как устаревшую. Новый commit берёт
 *       фактический id из мира.</li>
 *   <li><b>неверная позиция.</b> Если зафиксировать запись по опорному/нажатому блоку, а не по
 *       позиции установки, запись попадает на не-источник и удаляется; «поиск ближайшего» ничего
 *       не находит. Новый commit ставит запись по реальному блоку-источнику.</li>
 * </ol>
 *
 * <p>Это logic-only модель: реальный {@code BlockItem.place}/мир не вызываются.</p>
 */
public final class PlacementModel {

	/** Клетка мира. */
	public record Pos(int x, int y, int z) {
		Pos relative(int dx, int dy, int dz) {
			return new Pos(x + dx, y + dy, z + dz);
		}
	}

	/** Поставленный блок: {@code blockId} либо {@code null} для пустого/обычного блока. */
	public static final class World {
		private final Map<String, String> blocks = new HashMap<>();

		public void set(Pos pos, String blockId) {
			if (blockId == null) {
				blocks.remove(key(pos));
			} else {
				blocks.put(key(pos), blockId);
			}
		}

		public String get(Pos pos) {
			return blocks.get(key(pos));
		}

		private static String key(Pos pos) {
			return pos.x() + "," + pos.y() + "," + pos.z();
		}
	}

	/** Запись хранилища. */
	public record Record(Pos pos, String expectedBlockId) {
	}

	/** Известные «управляемые» id источников (все — вид TORCH для теста). */
	private static boolean managed(String blockId) {
		return blockId != null && (blockId.equals("minecraft:torch") || blockId.equals("minecraft:wall_torch"));
	}

	private final List<Record> store = new ArrayList<>();

	/** Старый commit: пишет по позиции, ожидаемый id берёт «как передали». */
	public void commitOld(Pos placePos, String expectedId) {
		store.add(new Record(placePos, expectedId));
	}

	/** Новый commit: фактическую позицию и id определяет по миру (см. resolve). */
	public void commitNew(World world, Pos placementPos, String kind) {
		Pos pos = resolvePosition(world, placementPos, kind);
		if (pos == null) {
			return;
		}
		store.add(new Record(pos, world.get(pos)));
	}

	/** Аналог {@code LightSourceService.resolvePlacementTarget}: сначала placementPos, затем 6 соседей. */
	public static Pos resolvePosition(World world, Pos placementPos, String kind) {
		if (isManagedKind(world.get(placementPos), kind)) {
			return placementPos;
		}
		int[][] offsets = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
		for (int[] o : offsets) {
			Pos candidate = placementPos.relative(o[0], o[1], o[2]);
			if (isManagedKind(world.get(candidate), kind)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean isManagedKind(String blockId, String kind) {
		if (!managed(blockId)) {
			return false;
		}
		// В тесте оба факельных id относятся к виду TORCH.
		return "TORCH".equals(kind);
	}

	/** Один серверный тик: удаляет записи, не совпадающие с фактическим блоком (как {@code tickLevel}). */
	public void tickLevel(World world) {
		store.removeIf(record -> !managed(world.get(record.pos()))
				|| !record.expectedBlockId().equals(world.get(record.pos())));
	}

	public int size() {
		return store.size();
	}

	public List<Record> entries() {
		return new ArrayList<>(store);
	}

	/** Есть ли запись ровно по данной позиции. */
	public boolean hasRecordAt(Pos pos) {
		for (Record r : store) {
			if (r.pos().equals(pos)) {
				return true;
			}
		}
		return false;
	}
}

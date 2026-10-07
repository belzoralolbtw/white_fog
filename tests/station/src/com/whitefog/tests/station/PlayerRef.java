package com.whitefog.tests.station;

import java.util.HashMap;
import java.util.Map;

/**
 * Модель игрока для sandbox этапа 1.4: инвентарь (по id предмета), координаты, здоровье,
 * creative-флаг, sneak-флаг. Чистая логика без Minecraft.
 */
public final class PlayerRef {
	public final String uuid;
	public final String dimension;
	public double x;
	public double y;
	public double z;
	public final float maxReach;
	public boolean creative;
	/** Полон ли инвентарь (модель: новый предмет не влезает). */
	public boolean inventoryFull;
	public boolean shiftHeld;
	public float health = 20.0F;

	/** Количество каждого предмета в инвентаре. */
	public final Map<String, Integer> counts = new HashMap<>();

	public PlayerRef(String uuid, String dimension, double x, double y, double z, float maxReach, boolean creative) {
		this.uuid = uuid;
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.maxReach = maxReach;
		this.creative = creative;
	}

	public PlayerRef at(double nx, double ny, double nz) {
		PlayerRef copy = new PlayerRef(this.uuid, this.dimension, nx, ny, nz, this.maxReach, this.creative);
		copy.counts.putAll(this.counts);
		copy.inventoryFull = this.inventoryFull;
		copy.shiftHeld = this.shiftHeld;
		copy.health = this.health;
		return copy;
	}

	public int count(String itemId) {
		return this.counts.getOrDefault(itemId, 0);
	}

	public void give(String itemId, int n) {
		this.counts.merge(itemId, n, Integer::sum);
	}

	/** Пытается положить предмет в инвентарь. Возвращает {@code false}, если инвентарь полон. */
	public boolean add(String itemId, int n) {
		if (this.inventoryFull) {
			return false;
		}
		give(itemId, n);
		return true;
	}

	public boolean has(String itemId, int n) {
		return count(itemId) >= n;
	}

	public boolean remove(String itemId, int n) {
		int have = count(itemId);
		if (have < n) {
			return false;
		}
		if (have == n) {
			this.counts.remove(itemId);
		} else {
			this.counts.put(itemId, have - n);
		}
		return true;
	}

	public boolean withinReach(SimWorld.StationKey key) {
		double dx = this.x - (key.x() + 0.5);
		double dy = this.y - (key.y() + 0.5);
		double dz = this.z - (key.z() + 0.5);
		return (dx * dx + dy * dy + dz * dz) <= (this.maxReach + 1.0) * (this.maxReach + 1.0);
	}
}

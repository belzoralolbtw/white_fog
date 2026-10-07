package com.whitefog.tests.breaktimer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Простая реализация {@link WorldModel} для sandbox-прототипа. */
public final class SimWorld implements WorldModel {
	private final Map<BlockKey, BlockKind> blocks = new HashMap<>();
	private final Set<BlockKey> unloaded = new HashSet<>();
	private final Set<BlockKey> filled = new HashSet<>();
	private int changes = 0;
	private int drops = 0;

	public void put(BlockKey key, BlockKind kind) {
		blocks.put(key, kind);
	}

	/** Помечает станцию наполненной/пустой без смены категории блока. */
	public void setFilled(BlockKey key, boolean value) {
		if (value) {
			filled.add(key);
		} else {
			filled.remove(key);
		}
	}

	/** Помечает чанк выгруженным (блок остаётся в карте, но недоступен). */
	public void unload(BlockKey key) {
		unloaded.add(key);
	}

	public void load(BlockKey key) {
		unloaded.remove(key);
	}

	@Override
	public boolean isChunkLoaded(BlockKey key) {
		return !unloaded.contains(key);
	}

	@Override
	public boolean isAir(BlockKey key) {
		return !blocks.containsKey(key);
	}

	@Override
	public BlockKind blockKind(BlockKey key) {
		return blocks.get(key);
	}

	@Override
	public boolean isFilled(BlockKey key) {
		return filled.contains(key);
	}

	@Override
	public void setAir(BlockKey key) {
		blocks.remove(key);
		filled.remove(key);
		changes++;
	}

	@Override
	public void spawnDrop(BlockKey key, int count) {
		drops += count;
	}

	@Override
	public int changeCount() {
		return changes;
	}

	@Override
	public int dropCount() {
		return drops;
	}
}

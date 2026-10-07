package com.whitefog.tests.breaktimer;

/**
 * Мир, в котором работает таймер. В прототипе — симуляция; в реальном моде это
 * {@code ServerLevel} + {@code BlockState} (см. AGENTS.md «этап 1.3», javap-проверено).
 */
public interface WorldModel {
	/** Загружен ли чанк блока. Аналог проверки доступности чанка сервером. */
	boolean isChunkLoaded(BlockKey key);

	/** Пуст ли блок (воздух). Аналог {@code BlockState#isAir()}. */
	boolean isAir(BlockKey key);

	/** Категория блока или {@code null}, если воздух/неизвестно. */
	BlockKind blockKind(BlockKey key);

	/**
	 * Наполнена ли станция/ёмкость ПРЯМО СЕЙЧАС. Моделирует {@code BlockBreakRules#isFilled}:
	 * содержимое можно добавить без смены {@code BlockState}, поэтому проверяется динамически.
	 */
	boolean isFilled(BlockKey key);

	/** Убирает блок (commit). Аналог финального изменения состояния сервером. */
	void setAir(BlockKey key);

	/** Спавнит дроп у позиции. Аналог {@code Block#popResource}. */
	void spawnDrop(BlockKey key, int count);

	// --- счётчики для проверки «блок изменился максимум один раз» / «один дроп» ---

	int changeCount();

	int dropCount();
}

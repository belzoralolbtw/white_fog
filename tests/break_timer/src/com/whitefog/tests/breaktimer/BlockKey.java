package com.whitefog.tests.breaktimer;

/**
 * Позиция блока вместе с измерением — аналог {@code BlockPos} + {@code ServerLevel}.
 *
 * <p>Измерение входит в ключ, поэтому блок в другом измерении по тем же координатам
 * НЕ является целью активного таймера (проверка «break при смене измерения»).</p>
 */
public record BlockKey(String dimension, int x, int y, int z) {
	public BlockKey withDimension(String otherDimension) {
		return new BlockKey(otherDimension, x, y, z);
	}

	@Override
	public String toString() {
		return dimension + "@" + x + "," + y + "," + z;
	}
}

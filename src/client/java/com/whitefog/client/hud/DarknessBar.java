package com.whitefog.client.hud;

/**
 * Чистая тонкая полоска тьмы/exposure (этап 1.9, интеграция в Work Panel по запросу пользователя).
 *
 * <p>Значение берётся ТОЛЬКО из серверного снимка тьмы ({@code DarknessSnapshotPayload.exposure},
 * 0..100) и нормализуется в 0..1 общей функцией {@link DarkHudLayout#exposureNormalized(int)}.
 * Клиент не пересчитывает exposure по картинке. Сглаживание — тем же
 * {@link DarkHudLayout#approach(double,double,double)} по frame-dt, что и у остальных виджетов
 * (frame-rate independent). Геометрия (Y, ширина дорожки) — общие константы
 * {@link DarkHudLayout}; класс не ссылается на Minecraft.</p>
 *
 * <p>Полоска — единый визуальный язык с бывшим статус-виджетом: дорожка тёмная, заливка —
 * градиент accent→danger, толщина {@link DarkHudLayout#BAR_HEIGHT} (после scale — 1 реальный px).</p>
 */
public final class DarknessBar {
	private double value;

	/** Приближает значение полоски к нормализованному exposure за кадр. */
	public void updateAnimations(int exposure, double dt) {
		double target = DarkHudLayout.exposureNormalized(exposure);
		this.value = DarkHudLayout.approach(this.value, target, DarkHudLayout.APPROACH_SPEED * dt);
	}

	/** Текущее сглаженное значение 0..1. */
	public double value() {
		return this.value;
	}

	/** Сброс в 0 (например при выходе из мира). */
	public void reset() {
		this.value = 0.0;
	}

	/** Логическая ширина заливки внутри панели шириной {@code panelWidthLogical}. */
	public int filledWidth(int panelWidthLogical) {
		int track = DarkHudLayout.darknessBarTrackWidth(panelWidthLogical);
		return (int) Math.round(track * this.value);
	}
}

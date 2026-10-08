package com.whitefog.tests.darknessvisual;

/**
 * Эталон vanilla-пульсации Darkness, воспроизведённый по реальному байткоду 26.2
 * ({@code LightmapRenderStateExtractor.calculateDarknessScale}).
 *
 * <pre>
 * private float calculateDarknessScale(LivingEntity entity, float f, float partialTick) {
 *     float a = 0.45f * f;
 *     return Math.max(0.0f, Mth.cos((entity.tickCount - partialTick) * (float) Math.PI * 0.025f) * a);
 * }
 * </pre>
 *
 * <p>Используется только для доказательства, что vanilla-слагаемое ПУЛЬСИРУЕТ, а наше постоянное —
 * нет (max-min по периоду). В основной код мода не переносится.</p>
 */
public final class VanillaDarknessReference {
	private VanillaDarknessReference() {
	}

	/** Пиковое постоянное слагаемое vanilla при blend=1, option=1. */
	public static final float VANILLA_PEAK = 0.45f;

	/** Период пульсации в тиках: cos((t)*PI*0.025) => период 2/PI/0.025 = 80 тиков. */
	public static final int PULSE_PERIOD_TICKS = 80;

	/**
	 * Значение {@code state.darknessEffectScale} vanilla (option=1): {@code max(0, cos)*0.45*blend}.
	 *
	 * @param tickCount   тик сущности (в extract передаётся 1.0f как partialTick — см. байткод)
	 * @param blendFactor фактор эффекта (обычно 1.0 при активном Darkness)
	 */
	public static float pulse(int tickCount, float blendFactor) {
		float a = VANILLA_PEAK * blendFactor;
		double c = Math.cos(((double) tickCount - 1.0) * Math.PI * 0.025);
		return (float) Math.max(0.0, c * a);
	}
}

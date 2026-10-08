package com.whitefog.tests.darknessvisual;

/**
 * Огибающая визуального затемнения: значение в {@code [0,1]}, продвигаемое по фактическому
 * времени кадра (frame-delta), а не по тикам.
 *
 * <p>Свойства, которые проверяет sandbox:</p>
 * <ul>
 *   <li><b>Монотонность</b> при постоянной цели — шаги одного знака, без колебаний (нет пульсации);</li>
 *   <li><b>Без перехлёста</b> — при {@code |target - value| < rate*dt} значение ставится ровно в цель,
 *       поэтому выход никогда не перескакивает цель и не выходит за {@code [0,1]};</li>
 *   <li><b>Частотно-независимость</b> — за одинаковое реальное время при разном dt результат совпадает
 *       по завершении перехода (ограничение шага линейно по dt);</li>
 *   <li><b>Ограниченность</b> — всегда {@code [0,1]}.</li>
 * </ul>
 */
public final class VisualEnvelope {
	private float value;

	/** Текущее значение огибающей в {@code [0,1]}. */
	public float value() {
		return this.value;
	}

	/** Принудительно задаёт значение (clamp). */
	public void reset(float v) {
		this.value = DarknessVisualPolicy.clamp01(v);
	}

	/**
	 * Продвигает огибающую к цели за {@code dtSeconds} со скоростью {@code ratePerSecond}.
	 *
	 * @return фактическое изменение (со знаком) — удобно для проверки монотонности.
	 */
	public float advance(float target, double dtSeconds, float ratePerSecond) {
		float t = DarknessVisualPolicy.clamp01(target);
		if (dtSeconds <= 0.0 || ratePerSecond <= 0.0f) {
			return 0.0f;
		}
		float before = this.value;
		float maxStep = (float) (ratePerSecond * dtSeconds);
		float diff = t - before;
		if (diff > maxStep) {
			diff = maxStep;
		} else if (diff < -maxStep) {
			diff = -maxStep;
		}
		this.value = DarknessVisualPolicy.clamp01(before + diff);
		return this.value - before;
	}
}

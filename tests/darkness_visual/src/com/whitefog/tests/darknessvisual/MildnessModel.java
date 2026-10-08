package com.whitefog.tests.darknessvisual;

/**
 * Чистая модель клиентского визуального гейта модовой тьмы.
 *
 * <p>Определяет, активна ли ИМЕННО модовая тьма, и ведёт огибающую с учётом:</p>
 * <ul>
 *   <li>последнего серверного snapshot ({@code lightExposure >= 50});</li>
 *   <li>свежести snapshot ({@link DarknessVisualPolicy#SNAPSHOT_STALE_SECONDS});</li>
 *   <li>жизненного состояния и режима игры (только живой survival/adventure);</li>
 *   <li>явного сброса (disconnect) — тогда данные сразу недостоверны.</li>
 * </ul>
 *
 * <p>Ничего не знает о Minecraft и не имеет побочных эффектов, кроме собственного состояния,
 * поэтому полностью детерминирована для заданной последовательности кадров.</p>
 */
public final class MildnessModel {
	private final VisualEnvelope envelope = new VisualEnvelope();

	private boolean hasSnapshot;
	private int lastExposure;
	private double secondsSinceSnapshot;
	private double secondsSinceTargetActive = Double.MAX_VALUE;
	private boolean active;
	private boolean ownedTail;

	/** Принят новый серверный snapshot (обновляет exposure и свежесть). */
	public void onSnapshot(int exposure) {
		this.lastExposure = exposure;
		this.secondsSinceSnapshot = 0.0;
		this.hasSnapshot = true;
	}

	/** Отключение/выход из мира: данные сервера недостоверны, мгновенно неактивно. */
	public void onDisconnect() {
		this.hasSnapshot = false;
		this.lastExposure = 0;
		this.secondsSinceSnapshot = Double.MAX_VALUE;
		// Хвост принадлежности тоже сбрасываем: после disconnect модовая тьма не продолжается.
		this.secondsSinceTargetActive = Double.MAX_VALUE;
	}

	/** Достоверен ли последний snapshot по времени. */
	public boolean snapshotFresh() {
		return this.hasSnapshot && this.secondsSinceSnapshot <= DarknessVisualPolicy.SNAPSHOT_STALE_SECONDS;
	}

	/** Должна ли модовая тьма визуально затемнять кадр (до учёта огибающей). */
	public boolean targetActive(boolean alive, boolean creative, boolean spectator) {
		return snapshotFresh()
				&& this.lastExposure >= DarknessVisualPolicy.MOD_DARK_EXPOSURE_THRESHOLD
				&& alive
				&& !creative
				&& !spectator;
	}

	/** Совместимый вызов без учёта собственного Darkness-эффекта (эквивалент {@code selfDarkness=false}). */
	public float frame(double dtSeconds, boolean alive, boolean creative, boolean spectator) {
		return frame(dtSeconds, alive, creative, spectator, false);
	}

	/**
	 * Один кадр: продвигает свежесть, «хвост принадлежности» и огибающую.
	 *
	 * <p>Цель огибающей — {@code hold = targetActive || ownedTail}. {@code ownedTail} держит
	 * адаптер в подавляющем режиме, пока у игрока ЕЩЁ ЕСТЬ Darkness-эффект и модовая активность
	 * была недавно ({@link DarknessVisualPolicy#OWNED_TAIL_SECONDS}) — чтобы после ухода exposure
	 * ниже порога ванильная пульсация не вернулась на остаток уже наложенного эффекта (40 тиков).</p>
	 *
	 * @return фактическое изменение огибающей (со знаком).
	 */
	public float frame(double dtSeconds, boolean alive, boolean creative, boolean spectator, boolean selfDarkness) {
		if (dtSeconds > 0.0) {
			if (this.secondsSinceSnapshot < Double.MAX_VALUE) {
				this.secondsSinceSnapshot += dtSeconds;
			}
		}
		boolean target = targetActive(alive, creative, spectator);
		if (target) {
			this.secondsSinceTargetActive = 0.0;
		} else if (this.secondsSinceTargetActive < Double.MAX_VALUE) {
			this.secondsSinceTargetActive += Math.max(0.0, dtSeconds);
		}
		this.ownedTail = !target
				&& this.hasSnapshot
				&& selfDarkness
				&& this.secondsSinceTargetActive <= DarknessVisualPolicy.OWNED_TAIL_SECONDS;
		boolean hold = target || this.ownedTail;
		float rate = hold ? DarknessVisualPolicy.FADE_IN_PER_SECOND : DarknessVisualPolicy.FADE_OUT_PER_SECOND;
		float delta = this.envelope.advance(hold ? 1.0f : 0.0f, dtSeconds, rate);
		this.active = hold || this.envelope.value() > 1.0e-4f;
		return delta;
	}

	/** Текущее значение огибающей. */
	public float envelope() {
		return this.envelope.value();
	}

	/** Активен ли адаптер (миксины применяют правки только при {@code true}). */
	public boolean active() {
		return this.active;
	}

	/** Действует ли сейчас «хвост принадлежности» (подавление остатка модового эффекта). */
	public boolean ownedTail() {
		return this.ownedTail;
	}

	/** Секунды с последнего targetActive (для проверок хвоста). */
	public double secondsSinceTargetActive() {
		return this.secondsSinceTargetActive;
	}

	// ------------------------------------------------------------------
	// Выходы адаптера для текущей огибающей (blend=1 => без внешнего Darkness-фактора)
	// ------------------------------------------------------------------

	public float brightnessFloor() {
		return DarknessVisualPolicy.brightnessFloor(this.envelope.value(), 1.0f);
	}

	public float darknessOffset() {
		return DarknessVisualPolicy.darknessOffset(this.envelope.value(), 1.0f);
	}

	public float fogEnd(float vanillaFogEnd) {
		return DarknessVisualPolicy.fogEnd(vanillaFogEnd, this.envelope.value());
	}

	public float fogDarkness(float vanillaDarkness) {
		return DarknessVisualPolicy.fogDarkness(vanillaDarkness, this.envelope.value());
	}

	// Доступ для тестов.
	public int lastExposure() {
		return this.lastExposure;
	}

	public boolean hasSnapshot() {
		return this.hasSnapshot;
	}

	public double secondsSinceSnapshot() {
		return this.secondsSinceSnapshot;
	}
}

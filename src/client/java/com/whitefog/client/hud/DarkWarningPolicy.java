package com.whitefog.client.hud;

/**
 * Чистая политика визуального предупреждения о тьме (этап 1.9, режим action-bar).
 *
 * <p>Сохраняет семантику ROADMAP §1.9: предупреждение срабатывает при {@code exposure >= 75}
 * («Найди свет») и усиливается при {@code exposure >= 90} («Тьма истощает тебя»). В отличие от
 * первоначального HUD-окна, текст теперь показывается обычным всплывающим action-bar сообщением —
 * тем же способом, что и «Слишком крепко — нужен инструмент» ({@code LocalPlayer#sendOverlayMessage},
 * javap-проверено; см. {@code MultiPlayerGameModeMixin} в этом проекте). Никакого прямоугольника HUD
 * и никакого {@code GuiGraphicsExtractor} предупреждения не используют.</p>
 *
 * <p>Класс НЕ ссылается на Minecraft: пороги, смена причины и визуальный cooldown моделируются
 * детерминированно, что позволяет проверить их sandbox-ом. Значение exposure — серверное
 * (из {@code DarknessSnapshotPayload}), клиент его не пересчитывает.</p>
 *
 * <p>Правила:</p>
 * <ul>
 *     <li>{@link #reasonFor(int)}: {@code 0..74 -> NONE}, {@code 75..89 -> FIND_LIGHT},
 *         {@code >=90 -> DRAIN};</li>
 *     <li>смена причины (в т.ч. в {@code NONE}) запускает cooldown {@link #COOLDOWN_TICKS}
 *         (40 тиков) — во время него активная причина не меняется и ничего не отправляется;</li>
 *     <li>после cooldown новая причина становится активной и отправляется РОВНО ОДИН раз;</li>
 *     <li>та же причина повторно не отправляется (без спама каждый кадр);</li>
 *     <li>{@link #REASON_NONE} никогда не отправляется.</li>
 * </ul>
 */
public final class DarkWarningPolicy {
	/** Причина отсутствует — ничего не показывать. */
	public static final int REASON_NONE = 0;
	/** «Найди свет» (exposure ≥ 75). */
	public static final int REASON_FIND_LIGHT = 1;
	/** «Тьма истощает тебя» (exposure ≥ 90). */
	public static final int REASON_DRAIN = 2;

	/** Порог предупреждения «Найди свет». */
	public static final int EXPOSURE_FIND_LIGHT = 75;
	/** Порог предупреждения «Тьма истощает тебя». */
	public static final int EXPOSURE_DRAIN = 90;
	/** Визуальный cooldown при смене причины (тиков, 40 = 2 c). */
	public static final int COOLDOWN_TICKS = 40;

	private int pendingReason = REASON_NONE;
	private int activeReason = REASON_NONE;
	private int lastEmitted = REASON_NONE;
	private double cooldownTicks;

	/** Причина предупреждения по серверному exposure (клиент только сопоставляет пороги). */
	public static int reasonFor(int exposure) {
		if (exposure >= EXPOSURE_DRAIN) {
			return REASON_DRAIN;
		}
		if (exposure >= EXPOSURE_FIND_LIGHT) {
			return REASON_FIND_LIGHT;
		}
		return REASON_NONE;
	}

	/**
	 * Обновляет политику за кадр (или тик) и возвращает причину, которую НУЖНО отправить этим
	 * вызовом, либо {@link #REASON_NONE}. Cooldown уменьшается на {@code dt * 20} тиков, поэтому
	 * результат не зависит от FPS. Одна причина отправляется ровно один раз за «вход» в неё.
	 */
	public int update(int exposure, double dtSeconds) {
		int target = reasonFor(exposure);
		if (target != this.pendingReason) {
			this.pendingReason = target;
			this.cooldownTicks = COOLDOWN_TICKS;
		}
		if (this.cooldownTicks > 0.0) {
			this.cooldownTicks -= Math.max(0.0, dtSeconds) * 20.0;
			if (this.cooldownTicks < 0.0) {
				this.cooldownTicks = 0.0;
			}
		}
		if (this.cooldownTicks <= 0.0) {
			this.activeReason = this.pendingReason;
		}
		if (this.activeReason == REASON_NONE) {
			this.lastEmitted = REASON_NONE;
			return REASON_NONE;
		}
		if (this.activeReason != this.lastEmitted) {
			this.lastEmitted = this.activeReason;
			return this.activeReason;
		}
		return REASON_NONE;
	}

	/** Полный сброс (например при выходе из мира): следующая причина отправится заново. */
	public void reset() {
		this.pendingReason = REASON_NONE;
		this.activeReason = REASON_NONE;
		this.lastEmitted = REASON_NONE;
		this.cooldownTicks = 0.0;
	}

	/** Активная (уже показанная) причина. */
	public int activeReason() {
		return this.activeReason;
	}

	/** Причина, к которой политика движется после cooldown. */
	public int pendingReason() {
		return this.pendingReason;
	}

	/** Остаток cooldown в тиках (0, если cooldown истёк). */
	public int cooldownTicksRemaining() {
		return (int) Math.ceil(this.cooldownTicks);
	}
}

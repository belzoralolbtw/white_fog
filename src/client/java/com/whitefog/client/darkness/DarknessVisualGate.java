package com.whitefog.client.darkness;

import com.whitefog.client.ClientDarknessState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Клиентский гейт визуального адаптера модовой тьмы (этап 1.5 — визуальный hotfix).
 *
 * <p>Определяет, активна ли ИМЕННО модовая тьма, и ведёт bounded-огибающую по фактическому
 * времени кадра (frame-delta), а не по тикам. Огибающая — единственный источник для миксинов:
 * {@code LightmapRenderStateExtractorMixin} заменяет ванильный пульсирующий
 * {@code darknessEffectScale} постоянным значением, а {@code DarknessFogEnvironmentMixin}
 * отодвигает/осветляет туман. При {@code !active()} миксины НЕ вмешиваются (ровно ваниль).</p>
 *
 * <h2>Гейт (условия «это модовая тьма»)</h2>
 * <ul>
 *   <li>последний серверный snapshot: {@code lightExposure >= 50};</li>
 *   <li>снимок свежий ({@link DarknessVisualConfig#SNAPSHOT_STALE_SECONDS}; heartbeat сервера 5 с);</li>
 *   <li>локальный игрок жив и не creative/spectator (только local player/camera, не remote entity);</li>
 *   <li>учитывается только локальный игрок: гейт обновляется из per-frame хука по {@code Minecraft.player}.</li>
 * </ul>
 *
 * <h2>Gate tail (документированная причина)</h2>
 * <p>Когда exposure уходит ниже 50, сервер прекращает обновлять Darkness, но уже наложенный
 * эффект живёт ещё до 40 тиков (~2 с). Если просто погасить адаптер по порогу, ванильная
 * пульсация/чёрный туман вернутся на это окно. Поэтому после ухода targetActive гейт ещё
 * {@link DarknessVisualConfig#OWNED_TAIL_SECONDS} секунд считает присутствующий Darkness-эффект
 * СВОИМ ({@code ownedTail}) и продолжает подавлять ванильную пульсацию, пока огибающая плавно
 * спадает. Чужой Darkness, появившийся вне этого окна (или при отсутствии модовой активности),
 * не трогается: {@code ownedTail} требует недавней модовой активности И наличия эффекта.</p>
 *
 * <h2>Сброс</h2>
 * <p>{@link #reset()} вызывается при disconnect (и когда нет клиента/игрока): огибающая,
 * «хвост» и active сбрасываются, чтобы не осталось залипшего затемнения.</p>
 */
@Environment(EnvType.CLIENT)
public final class DarknessVisualGate {
	/** Единственный клиентский экземпляр; миксины читают его после per-frame обновления. */
	public static final DarknessVisualGate INSTANCE = new DarknessVisualGate();

	private float envelope;
	private float blend;
	private boolean active;
	private double secondsSinceTargetActive = Double.MAX_VALUE;
	private boolean hasHadSnapshot;

	private DarknessVisualGate() {
	}

	/** Мгновенный сброс (disconnect/нет клиента): никакого залипшего затемнения. */
	public void reset() {
		this.envelope = 0.0f;
		this.blend = 0.0f;
		this.active = false;
		this.secondsSinceTargetActive = Double.MAX_VALUE;
		this.hasHadSnapshot = false;
	}

	/**
	 * Один кадр: продвигает огибающую по frame-delta. Вызывается из per-frame хука
	 * Fabric {@code LevelExtractionEvents.END_EXTRACTION} (см. {@code WhiteFogClient}).
	 *
	 * @param dtSeconds          фактическое время кадра в секундах (из {@code DeltaTracker}); ограничивается
	 * @param state              клиентский кэш снимка тьмы (источник exposure и свежести)
	 * @param alive              локальный игрок жив
	 * @param creative           локальный игрок в creative
	 * @param spectator          локальный игрок в spectator
	 * @param selfDarkness       у локального игрока сейчас есть эффект {@code minecraft:darkness}
	 * @param userDarknessScale  клиентская настройка {@code options.darknessEffectScale} (0..1, доступность)
	 */
	public void update(double dtSeconds, ClientDarknessState state, boolean alive, boolean creative,
			boolean spectator, boolean selfDarkness, double userDarknessScale) {
		double dt = dtSeconds;
		if (!(dt > 0.0)) {
			dt = 0.0;
		} else if (dt > DarknessVisualConfig.MAX_FRAME_SECONDS) {
			dt = DarknessVisualConfig.MAX_FRAME_SECONDS;
		}

		this.blend = DarknessVisualConfig.clamp01((float) userDarknessScale);

		boolean hasSnapshot = state != null && state.hasData();
		this.hasHadSnapshot |= hasSnapshot;
		boolean fresh = hasSnapshot
				&& state.secondsSinceUpdate() <= DarknessVisualConfig.SNAPSHOT_STALE_SECONDS;
		int exposure = state == null ? 0 : state.lightExposure();

		boolean targetActive = fresh
				&& exposure >= DarknessVisualConfig.MOD_DARK_EXPOSURE_THRESHOLD
				&& alive && !creative && !spectator;

		if (targetActive) {
			this.secondsSinceTargetActive = 0.0;
		} else if (this.secondsSinceTargetActive < Double.MAX_VALUE) {
			this.secondsSinceTargetActive += dt;
		}

		boolean ownedTail = !targetActive
				&& this.hasHadSnapshot
				&& selfDarkness
				&& this.secondsSinceTargetActive <= DarknessVisualConfig.OWNED_TAIL_SECONDS;
		boolean hold = targetActive || ownedTail;

		if (dt > 0.0) {
			float target = hold ? 1.0f : 0.0f;
			float rate = hold
					? DarknessVisualConfig.FADE_IN_PER_SECOND
					: DarknessVisualConfig.FADE_OUT_PER_SECOND;
			float maxStep = (float) (rate * dt);
			float diff = target - this.envelope;
			if (diff > maxStep) {
				diff = maxStep;
			} else if (diff < -maxStep) {
				diff = -maxStep;
			}
			this.envelope = DarknessVisualConfig.clamp01(this.envelope + diff);
		}

		// active = подавляем ваниль, пока есть цель/хвост ИЛИ пока огибающая ещё не догорела.
		this.active = hold || this.envelope > 1.0e-4f;
	}

	/** Активен ли адаптер: миксины применяют правки только при {@code true}. */
	public boolean active() {
		return this.active;
	}

	/** Текущая огибающая в [0,1]. */
	public float envelope() {
		return this.envelope;
	}

	/** Клиентская настройка {@code darknessEffectScale} в [0,1] (0 = пользователь отключил эффект). */
	public float blend() {
		return this.blend;
	}

	/** Итоговая сила {@code eff = envelope * blend}: множитель всех выходов адаптера. */
	public float strength() {
		return this.envelope * this.blend;
	}
}

package com.whitefog.tests.darknessvisual;

/**
 * Чистая модель параметров и выходных отображений клиентского визуального адаптера модовой тьмы.
 *
 * <p>Это sandbox: класс не зависит от Minecraft и повторяет только математику планируемого
 * client-only адаптера. Все входные/выходные величины ограничены (bounded) — это и есть предмет
 * проверки. Источник значений vanilla-механики — реальный байткод 26.2, прочитанный javap
 * (evidence в logs\_evidence_*.txt), а не «по памяти».</p>
 *
 * <h2>Почему именно эти рычаги (по байткоду 26.2)</h2>
 * <ul>
 *   <li><b>Lightmap.</b> {@code LightmapRenderStateExtractor.extract} пишет
 *       {@code state.brightness = max(0, gamma - f)}, {@code
 *       state.darknessEffectScale = max(0, cos((tick-pt)*PI*0.025) * 0.45 * f) * f}
 *       при {@code f = getEffectBlendFactor(DARKNESS, pt) * options.darknessEffectScale}.
 *       Шейдер {@code lightmap.fsh}: {@code color = color - DarknessScale}, затем
 *       {@code mix(color, notGamma(color), BrightnessFactor)}. Пульсацию даёт именно косинус.</li>
 *   <li><b>Fog.</b> {@code DarknessFogEnvironment.setupFog} схлопывает дистанции тумана до ~15 блоков;
 *       {@code getModifiedDarkness} возвращает {@code max(voidFactor, blendFactor)}, а
 *       {@code FogRenderer.computeFogColor} умножает цвет тумана на {@code square(1 - factor)}.</li>
 * </ul>
 *
 * <p>Адаптер (следующая фаза) заменяет пульсирующий {@code darknessEffectScale} на постоянную
 * умеренную величину, поднимает пол {@code brightness} и «отодвигает»/осветляет туман — только
 * когда активна ИМЕННО модовая тьма (последний серверный snapshot {@code exposure >= 50}).</p>
 */
public final class DarknessVisualPolicy {
	private DarknessVisualPolicy() {
	}

	// ------------------------------------------------------------------
	// Входной гейт: ТОЛЬКО модовая тьма
	// ------------------------------------------------------------------

	/** Порог exposure, при котором сервер накладывает vanilla Darkness (совпадает с DarknessConfig). */
	public static final int MOD_DARK_EXPOSURE_THRESHOLD = 50;

	/**
	 * Свежесть последнего snapshot в секундах. Сервер шлёт heartbeat каждые 100 тиков (5 с), поэтому
	 * отсутствие снимка дольше этого окна считается устареванием (сервер замолчал/вышли из мира).
	 */
	public static final double SNAPSHOT_STALE_SECONDS = 7.0;

	/**
	 * «Хвост принадлежности» (gate tail): сколько секунд после targetActive ещё присутствующий
	 * Darkness-эффект считается СВОИМ. Сервер держит эффект 60 тиков (3 с) и обновляет при остатке
	 * 40 тиков; при уходе exposure ниже порога эффект догорает до 60 + 22 (blend-advance) = 82 тика
	 * ≈ 4.1 с. Окно 5 с покрывает этот остаток: адаптер продолжает подавлять ванильную пульсацию,
	 * пока огибающая плавно спадает. Чужой Darkness старше окна не трогается.
	 */
	public static final double OWNED_TAIL_SECONDS = 5.0;

	/** Верхний предел шага за кадр (с) — защита от скачка огибающей после лага/паузы. */
	public static final double MAX_FRAME_SECONDS = 0.25;

	// ------------------------------------------------------------------
	// Огибающая (envelope): frame-delta, без пульсации
	// ------------------------------------------------------------------

	/**
	 * Скорость нарастания огибающей (единиц в секунду). Замедлена по сообщению игрока (полная сила
	 * за 2.5 с вместо 0.5 с) — только скорость визуального fade-in. Fade-out не менялся.
	 */
	public static final float FADE_IN_PER_SECOND = 0.4f;
	/** Скорость спада огибающей (единиц в секунду). */
	public static final float FADE_OUT_PER_SECOND = 1.0f;

	// ------------------------------------------------------------------
	// Умеренные пределы (continuous mild cap)
	// ------------------------------------------------------------------

	/**
	 * Максимальный «пол» brightness (0.35 = умеренный подъём кривой не-гаммы). Абсолютный light 0
	 * без ambient-света всё равно может остаться тёмным — это честное ограничение, а не обещание.
	 */
	public static final float MAX_BRIGHTNESS_FLOOR = 0.35f;

	/** Максимальное постоянное вычитание из lightmap (0.16 против vanilla-пика 0.45). */
	public static final float MAX_DARKNESS_OFFSET = 0.16f;

	/** Минимальная дистанция конца тумана модовой тьмы в блоках (vanilla-тьма схлопывает до ~15). */
	public static final float MIN_FOG_DISTANCE = 48.0f;

	/** Максимальный фактор затемнения цвета тумана (0.55 => square(1-0.55)=0.2, не в ноль). */
	public static final float MAX_FOG_DARKNESS = 0.55f;

	// ------------------------------------------------------------------
	// Отображения выходов (e in [0,1]); continuous, монотонные, без перехлёста
	// ------------------------------------------------------------------

	/** Пол brightness: {@code floor = MAX_BRIGHTNESS_FLOOR * e * blend}, clamp. */
	public static float brightnessFloor(float envelope, float blend) {
		return clamp01(envelope) * clamp01(blend) * MAX_BRIGHTNESS_FLOOR;
	}

	/** Постоянное вычитание из lightmap: {@code offset = MAX_DARKNESS_OFFSET * e * blend}. */
	public static float darknessOffset(float envelope, float blend) {
		return clamp01(envelope) * clamp01(blend) * MAX_DARKNESS_OFFSET;
	}

	/**
	 * Дистанция конца тумана: при {@code e=0} — ровно vanilla, при {@code e=1} — не меньше
	 * {@link #MIN_FOG_DISTANCE}. Линейная интерполяция => непрерывно и монотонно.
	 */
	public static float fogEnd(float vanillaFogEnd, float envelope) {
		float target = Math.max(vanillaFogEnd, MIN_FOG_DISTANCE);
		return lerp(clamp01(envelope), vanillaFogEnd, target);
	}

	/**
	 * Фактор затемнения цвета тумана: при {@code e=0} — ровно vanilla, при {@code e=1} — не больше
	 * {@link #MAX_FOG_DARKNESS}. Монотонно убывает по e, не выходит за пределы.
	 */
	public static float fogDarkness(float vanillaDarkness, float envelope) {
		float target = Math.min(vanillaDarkness, MAX_FOG_DARKNESS);
		return lerp(clamp01(envelope), vanillaDarkness, target);
	}

	/**
	 * Фактор {@code getModifiedDarkness} с сохранением void-тьмы: снижает только вклад эффекта
	 * до {@link #MAX_FOG_DARKNESS}, затем {@code max(voidFactor, ...)}. {@code eff=0} даёт ровно
	 * ванильный {@code max(voidFactor, blend)}.
	 */
	public static float fogDarknessFactor(float voidFactor, float blend, float eff) {
		float capped = Math.min(clamp01(blend), MAX_FOG_DARKNESS);
		float after = lerp(clamp01(eff), blend, capped);
		return Math.max(voidFactor, after);
	}

	// ------------------------------------------------------------------
	// Мелкие помощники (чистые)
	// ------------------------------------------------------------------

	public static float clamp01(float v) {
		if (v < 0.0f) {
			return 0.0f;
		}
		if (v > 1.0f) {
			return 1.0f;
		}
		return v;
	}

	public static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}
}

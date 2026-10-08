package com.whitefog.client.darkness;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Клиентские tunables визуального адаптера модовой тьмы (этап 1.5 — визуальный hotfix).
 *
 * <p>Только client source set: серверную механику ({@code LightExposureService}, формулу
 * exposure, эффект Darkness, штраф скорости и Condition) этот класс НЕ трогает. Значения
 * повторяют sandbox {@code tests/darkness_visual} (эталон чистой логики), а сами рычаги
 * обоснованы реальным байткодом 26.2 (см. Javadoc {@link DarknessVisualGate} и
 * {@code logs\_evidence_*.txt}).</p>
 *
 * <p><b>Зачем именно эти рычаги.</b> Пульсацию Darkness даёт косинус в
 * {@code LightmapRenderStateExtractor.calculateDarknessScale}, а near-black — короткие
 * дистанции тумана {@code DarknessFogEnvironment.setupFog} и умножение цвета тумана на
 * {@code square(1 - factor)} в {@code FogRenderer.computeFogColor}. Адаптер заменяет
 * пульсирующее значение lightmap постоянным умеренным, поднимает пол {@code brightness} и
 * отодвигает/осветляет туман — только пока активна ИМЕННО модовая тьма.</p>
 */
@Environment(EnvType.CLIENT)
public final class DarknessVisualConfig {
	private DarknessVisualConfig() {
	}

	// ------------------------------------------------------------------
	// Входной гейт: только модовая тьма
	// ------------------------------------------------------------------

	/** Порог exposure, при котором сервер накладывает vanilla Darkness (совпадает с DarknessConfig). */
	public static final int MOD_DARK_EXPOSURE_THRESHOLD = 50;

	/**
	 * Свежесть последнего снимка в секундах. Сервер шлёт heartbeat каждые 100 тиков (5 с),
	 * поэтому молчание дольше этого окна = устаревание (сервер замолчал/вышли из мира).
	 */
	public static final double SNAPSHOT_STALE_SECONDS = 7.0;

	/**
	 * «Хвост принадлежности» (gate tail): сколько секунд после targetActive гейт считает
	 * ещё присутствующий Darkness-эффект СВОИМ. Сервер держит эффект {@code DARK_EFFECT_DURATION_TICKS}
	 * (60 тиков = 3 с) и обновляет при остатке {@code DARK_EFFECT_REFRESH_REMAINING_TICKS} (40 тиков);
	 * при уходе exposure ниже порога эффект догорает ещё до
	 * {@code DARK_EFFECT_DURATION_TICKS + DARK_BLEND_ADVANCE_TICKS} (60 + 22 = 82 тика ≈ 4.1 с).
	 * Значение 5 с покрывает этот хвост с запасом, поэтому ванильная пульсация не успевает
	 * вернуться, пока огибающая плавно спадает; чужой Darkness вне этого окна не трогается.
	 */
	public static final double OWNED_TAIL_SECONDS = 5.0;

	// ------------------------------------------------------------------
	// Огибающая (envelope): frame-delta, без пульсации
	// ------------------------------------------------------------------

	/**
	 * Скорость нарастания огибающей (единиц в секунду). Замедлена по сообщению игрока: при 2.0
	 * затемнение «нарастало почти резко» (полная сила за 0.5 с). 0.4 даёт плавный ramp ≈ 2.5 с —
	 * именно скорость визуального fade-in, без изменения порога exposure, интервала samples,
	 * длительности/refresh vanilla Darkness и без возврата пульсации. Скорость fade-out не менялась.
	 */
	public static final float FADE_IN_PER_SECOND = 0.4f;
	/** Скорость спада огибающей (единиц в секунду). */
	public static final float FADE_OUT_PER_SECOND = 1.0f;

	/** Верхний предел шага за кадр (с): защита от скачка огибающей после лага/паузы. */
	public static final double MAX_FRAME_SECONDS = 0.25;

	// ------------------------------------------------------------------
	// Умеренные пределы (continuous mild cap)
	// ------------------------------------------------------------------

	/**
	 * Максимальный «пол» brightness (0.35 = умеренный подъём кривой не-гаммы). Абсолютный
	 * light 0 без ambient-света всё равно может остаться тёмным — это честное ограничение,
	 * а НЕ обещание fullbright.
	 */
	public static final float MAX_BRIGHTNESS_FLOOR = 0.35f;

	/** Максимальное постоянное вычитание из lightmap (0.16 против ванильного пика 0.45). */
	public static final float MAX_DARKNESS_OFFSET = 0.16f;

	/** Минимальная дистанция конца тумана модовой тьмы в блоках (ванильная тьма схлопывает до ~15). */
	public static final float MIN_FOG_DISTANCE = 48.0f;

	/** Максимальный фактор затемнения цвета тумана (0.55 =&gt; square(1-0.55)=0.2, не в ноль). */
	public static final float MAX_FOG_DARKNESS = 0.55f;

	// ------------------------------------------------------------------
	// Отображения выходов (eff in [0,1]); continuous, монотонные, без перехлёста
	// ------------------------------------------------------------------

	/**
	 * Постоянное вычитание из lightmap ({@code DarknessScale} в шейдере): заменяет
	 * пульсирующий косинус vanilla. {@code eff=0} даёт ровно 0 (vanilla no-op).
	 */
	public static float darknessOffset(float eff) {
		return clamp01(eff) * MAX_DARKNESS_OFFSET;
	}

	/** Пол brightness ({@code BrightnessFactor} в шейдере): {@code eff=0} — ровно vanilla no-op. */
	public static float brightnessFloor(float eff) {
		return clamp01(eff) * MAX_BRIGHTNESS_FLOOR;
	}

	/**
	 * Дистанция конца тумана: при {@code eff=0} — ровно vanilla, при {@code eff=1} — не меньше
	 * {@link #MIN_FOG_DISTANCE}. Линейная интерполяция =&gt; непрерывно и монотонно.
	 */
	public static float fogEnd(float vanillaFogEnd, float eff) {
		float target = Math.max(vanillaFogEnd, MIN_FOG_DISTANCE);
		return lerp(clamp01(eff), vanillaFogEnd, target);
	}

	/**
	 * Фактор затемнения цвета тумана в {@code getModifiedDarkness}. Снижает ТОЛЬКО вклад
	 * модового Darkness-эффекта (до {@link #MAX_FOG_DARKNESS}), сохраняя {@code voidFactor}
	 * (тьма ниже мира) через {@code max}: другую механику не отменяем. {@code eff=0} даёт
	 * ровно ванильный результат {@code max(voidFactor, blend)}.
	 */
	public static float fogDarknessFactor(float voidFactor, float blend, float eff) {
		float capped = Math.min(clamp01(blend), MAX_FOG_DARKNESS);
		float after = lerp(clamp01(eff), blend, capped);
		return Math.max(voidFactor, after);
	}

	/**
	 * Постоянный фактор затемнения тумана для активного МОД-гейта. НЕ зависит от ванильного
	 * {@code effect.getBlendFactor(entity, partialTick)} и косинуса: для мода blend фиксируется на
	 * полностью проявившемся значении {@code 1}, поэтому итог зависит только от огибающей
	 * {@code eff}. При стабильном гейте ({@code eff=1}) выход ровно {@link #MAX_FOG_DARKNESS};
	 * {@code voidFactor} (тьма ниже мира) сохраняется через {@code max}. Заменяет ванильную
	 * зависимость от partial tick — «дальняя пульсация» тьмы убрана.
	 */
	public static float fogDarknessFactorConstant(float voidFactor, float eff) {
		return fogDarknessFactor(voidFactor, 1.0f, eff);
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

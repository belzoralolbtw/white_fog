package com.whitefog.client.hud;

/**
 * Чистая (без Minecraft) раскладка компактного HUD света и целей (этап 1.9).
 *
 * <p>Все геометрические числа — именованные константы. Раскладка считается в «логических» пикселях
 * (до масштабирования), а наружные якоря (статус слева-снизу, цель справа-сверху, предупреждение
 * под прицелом) — в реальных GUI-пикселях. Весь HUD рисуется со {@link #SCALE}=0.5 (см.
 * {@code WhiteFogHud}), поэтому реальные размеры панели — округлённые логические * 0.5.</p>
 *
 * <p>Класс намеренно не ссылается на Minecraft: измерение текста передаётся через
 * {@link WidthMeasure}, что позволяет sandbox-у проверить перенос/обрезку/зоны детерминированно.</p>
 */
public final class DarkHudLayout {
	// --- Логические (до scale) константы панели ---
	/** Высота строки панели. */
	public static final int HEIGHT = 18;
	/** Внутренний отступ панели. */
	public static final int PAD = 4;
	/** Размер бокса иконки предмета. */
	public static final int ICON = 12;
	/** Зазор между иконкой и текстом. */
	public static final int TEXT_GAP = 4;
	/** Толщина мини-полоски exposure. */
	public static final int BAR_HEIGHT = 2;
	/** Зазор между строками. */
	public static final int ROW_GAP = 4;
	/** Максимальная ширина текста (логическая) до переноса. */
	public static final int MAX_TEXT_WIDTH = 240;

	// --- Масштаб и наружные якоря (реальные GUI-пиксели) ---
	/** Общий масштаб отрисовки HUD. */
	public static final float SCALE = 0.5f;
	/** Внешняя граница от края экрана. */
	public static final int OUTER_MARGIN = 4;
	/** Отступ снизу для статус-панели (место хотбара). */
	public static final int STATUS_BOTTOM_CLEARANCE = 44;
	/** Максимальная ширина панели цели (реальные GUI-пиксели). */
	public static final int GOAL_MAX_WIDTH = 120;
	/** Максимум строк цели. */
	public static final int GOAL_MAX_LINES = 2;
	/** Смещение предупреждения вниз от центра экрана. */
	public static final int WARNING_Y_OFFSET = 18;
	/** Минимальная ширина экрана для показа цели. */
	public static final int MIN_SCREEN_WIDTH = 320;
	/** Минимальная высота экрана для показа цели. */
	public static final int MIN_SCREEN_HEIGHT = 180;
	/** Число строк статус-панели (Свет / Тьма / Укрытие / Источник). */
	public static final int STATUS_ROWS = 4;
	/**
	 * Число строк рабочей панели света: заголовок, полоска тьмы ({@code Тьма: E%}), источник,
	 * топливо, переносной свет в руке, часы вечной ночи и подсказка действия. Единственная нижняя
	 * панель — все строки контент-сайз, нижний отступ оставляет место хотбару.
	 */
	public static final int WORK_PANEL_ROWS = 7;

	// --- Палитра (ARGB) ---
	/** Фон панели #18212B с прозрачностью ~80%. */
	public static final int COLOR_BACKGROUND = 0xCC18212B;
	/** Акцент #A8D8A8. */
	public static final int COLOR_ACCENT = 0xFFA8D8A8;
	/** Опасность #C77878. */
	public static final int COLOR_DANGER = 0xFFC77878;
	/** Текст #DBE5E9. */
	public static final int COLOR_TEXT = 0xFFDBE5E9;

	// --- Анимация ---
	/** Верхняя граница шага кадра (секунды). */
	public static final double MAX_DT_SECONDS = 0.05;
	/** Скорость сходимости approach (единиц в секунду). */
	public static final double APPROACH_SPEED = 8.0;

	/** Функция измерения ширины строки (в проде — {@code font::width}). */
	@FunctionalInterface
	public interface WidthMeasure {
		int width(String text);
	}

	private DarkHudLayout() {
	}

	// ------------------------------------------------------------------
	// Анимация
	// ------------------------------------------------------------------

	/** Ограничивает шаг кадра 0..0.05 c (защита от «прыжков» при лаге/паузе). */
	public static double clampDt(double dt) {
		if (!(dt > 0.0)) {
			return 0.0;
		}
		return Math.min(dt, MAX_DT_SECONDS);
	}

	/** Линейно приближает значение к цели не более чем на {@code maxDelta}. */
	public static double approach(double current, double target, double maxDelta) {
		if (!(maxDelta > 0.0)) {
			return current;
		}
		double diff = target - current;
		if (diff > maxDelta) {
			return current + maxDelta;
		}
		if (diff < -maxDelta) {
			return current - maxDelta;
		}
		return target;
	}

	/** Приближает альфу 0..255 той же функцией {@link #approach}. */
	public static int approachAlpha(int current, int target, double maxDelta) {
		return (int) Math.round(approach(current, target, maxDelta));
	}

	/** Тик остатка топлива → секунды округлением ВВЕРХ (1 тик → 1с). */
	public static int ceilSeconds(int ticks) {
		return ticks <= 0 ? 0 : (ticks + 19) / 20;
	}

	/** Нормализует и клипует серверный exposure 0..100 в значение полоски 0..1. */
	public static double exposureNormalized(int exposure) {
		int clamped = Math.max(0, Math.min(100, exposure));
		return clamped / 100.0;
	}

	// ------------------------------------------------------------------
	// Геометрия панелей
	// ------------------------------------------------------------------

	/** Логическая высота панели для {@code rows} строк. */
	public static int logicalHeight(int rows) {
		int safeRows = Math.max(1, rows);
		return safeRows * HEIGHT + (safeRows - 1) * ROW_GAP;
	}

	/** Реальная высота панели для {@code rows} строк. */
	public static int realHeight(int rows) {
		return Math.round(logicalHeight(rows) * SCALE);
	}

	/** Реальная ширина панели по логической ширине текста (текст ограничен {@link #MAX_TEXT_WIDTH}). */
	public static int realPanelWidth(int textLogicalWidth) {
		int text = Math.max(0, Math.min(textLogicalWidth, MAX_TEXT_WIDTH));
		int logical = 2 * PAD + ICON + TEXT_GAP + text;
		return Math.round(logical * SCALE);
	}

	/** X статус-панели (слева). */
	public static int statusX() {
		return OUTER_MARGIN;
	}

	/** Верх статус-панели (реальный): нижний край на {@code height-44}. */
	public static int statusTop(int screenHeight, int rows) {
		return screenHeight - STATUS_BOTTOM_CLEARANCE - realHeight(rows);
	}

	/** Логическая ширина, доступная тексту цели (не более {@link #GOAL_MAX_WIDTH} реальных). */
	public static int goalTextMaxLogical() {
		return Math.min(MAX_TEXT_WIDTH, GOAL_MAX_WIDTH * 2 - 2 * PAD);
	}

	/**
	 * X текста цели в логических координатах. Цель не имеет иконки, поэтому текст начинается ровно
	 * от внутреннего отступа {@link #PAD} — БЕЗ неиспользуемых {@link #ICON}/{@link #TEXT_GAP}.
	 * Контракт: {@code goalTextX() + ширина строки <= ширина панели} (см. {@link #goalPanelLogicalWidth}).
	 */
	public static int goalTextX() {
		return PAD;
	}

	/**
	 * Логическая ширина панели цели по логической ширине текста (текст ограничен
	 * {@link #goalTextMaxLogical()}). Контракт ширины: {@code 2*PAD + text}, поэтому при
	 * {@link #goalTextX()} = {@code PAD} правый край текста не выходит за панель.
	 */
	public static int goalPanelLogicalWidth(int textLogicalWidth) {
		int text = Math.max(0, Math.min(textLogicalWidth, goalTextMaxLogical()));
		return 2 * PAD + text;
	}

	/** Реальная ширина панели цели по логической ширине текста, ограниченная {@link #GOAL_MAX_WIDTH}. */
	public static int goalPanelWidth(int textLogicalWidth) {
		int text = Math.max(0, Math.min(textLogicalWidth, goalTextMaxLogical()));
		int logical = 2 * PAD + text;
		return Math.min(Math.round(logical * SCALE), GOAL_MAX_WIDTH);
	}

	/** Реальная высота панели цели по числу строк. */
	public static int goalPanelHeight(int lines) {
		return realHeight(Math.max(1, Math.min(lines, GOAL_MAX_LINES)));
	}

	/** X панели цели (справа). */
	public static int goalX(int screenWidth, int panelWidth) {
		return screenWidth - OUTER_MARGIN - panelWidth;
	}

	/** Y панели цели (сверху). */
	public static int goalY() {
		return OUTER_MARGIN;
	}

	/** Показывать ли цель на данном экране. */
	public static boolean goalVisible(int screenWidth, int screenHeight) {
		return screenWidth >= MIN_SCREEN_WIDTH && screenHeight >= MIN_SCREEN_HEIGHT;
	}

	/**
	 * Показывать ли отдельную панель «Текущее задание» в правом верхнем углу (запрос пользователя).
	 * Панель скрыта без gameplay-игрока (нет игрока/смерть/spectator/F1/открытый {@code Screen}),
	 * без серверного снимка (никаких выдуманных нулей) и на узком экране (&lt;320x180).
	 */
	public static boolean stageGoalVisible(boolean gameHidden, boolean hasData, int screenWidth, int screenHeight) {
		return !gameHidden && hasData && goalVisible(screenWidth, screenHeight);
	}

	// ------------------------------------------------------------------
	// Рабочая панель света (клавиша G) — общий стиль статуса/цели
	// ------------------------------------------------------------------

	/**
	 * Виден ли компактный статус, когда открыта рабочая панель {@code G}. Выбор детерминирован:
	 * пока панель {@code G} видима, статус скрывается — так исключено любое наложение двух панелей
	 * (у них общая лево-нижняя зона) и гарантирована одна согласованная лево-нижняя раскладка.
	 */
	public static boolean statusVisibleWithWorkPanel(boolean workPanelVisible) {
		return !workPanelVisible;
	}

	/** X рабочей панели света (слева) — тот же внешний отступ, что и у статуса. */
	public static int workPanelX() {
		return OUTER_MARGIN;
	}

	/** Максимальная логическая ширина текста рабочей панели — общий {@link #MAX_TEXT_WIDTH}. */
	public static int workPanelTextMaxLogical() {
		return MAX_TEXT_WIDTH;
	}

	/** Верх рабочей панели: та же нижняя граница, что и у статуса (место хотбара). */
	public static int workPanelTop(int screenHeight, int rows) {
		return statusTop(screenHeight, rows);
	}

	/** Реальная высота рабочей панели по общим строкам {@link #WORK_PANEL_ROWS}. */
	public static int workPanelHeight() {
		return realHeight(WORK_PANEL_ROWS);
	}

	/** Реальная ширина рабочей панели по логической ширине текста — общий {@link #realPanelWidth}. */
	public static int workPanelWidth(int textLogicalWidth) {
		return realPanelWidth(textLogicalWidth);
	}

	/** Индекс строки полоски тьмы в рабочей панели (сразу после заголовка). */
	public static int darknessBarRow() {
		return 1;
	}

	/**
	 * Логическая Y-координата полоски тьмы — у нижней кромки своей строки (та же формула, что и в
	 * бывшем статус-виджете {@code ExposureWidget}), поэтому текст строки остаётся сверху.
	 */
	public static int darknessBarLogicalY() {
		return darknessBarRow() * rowStepLogical() + HEIGHT - BAR_HEIGHT - 2;
	}

	/** Логическая ширина дорожки полоски тьмы (внутренняя ширина панели между отступами). */
	public static int darknessBarTrackWidth(int panelWidthLogical) {
		return Math.max(1, panelWidthLogical - PAD * 2);
	}

	/** X центрированного предупреждения. */
	public static int warningX(int screenWidth, int textWidth) {
		return (screenWidth - textWidth) / 2;
	}

	/** Y предупреждения (под прицелом). */
	public static int warningY(int screenHeight) {
		return screenHeight / 2 + WARNING_Y_OFFSET;
	}

	// ------------------------------------------------------------------
	// Политика видимости HUD (чистый контракт без Minecraft)
	// ------------------------------------------------------------------

	/**
	 * Показывать ли компактный HUD этапа 1.9 (статус «Свет/Тьма/Укрытие/Источник», панель цели и
	 * предупреждение под прицелом). По решению пользователя отображение этого слоя отключено:
	 * production-классы и данные сохранены для будущего использования, но кадр их не рисует.
	 * Контракт возвращает {@code false} всегда.
	 */
	public static boolean shouldShowCompactStageHud() {
		return false;
	}

	/**
	 * Work Panel света (клавиша {@code G}) видна постоянно в обычном gameplay. Видимость НЕ зависит
	 * от удержания клавиши: {@code G} остаётся отдельным быстрым действием заправки
	 * ({@code consumeClick} + C2S payload, см. {@code WhiteFogClient}) и не управляет панелью.
	 */
	public static boolean workPanelAlwaysVisible() {
		return true;
	}

	/**
	 * Итоговая видимость Work Panel: постоянна, пока gameplay-HUD не скрыт целиком (нет игрока,
	 * смерть, наблюдатель, F1, открытый {@code Screen}).
	 */
	public static boolean workPanelVisible(boolean gameHidden) {
		return workPanelAlwaysVisible() && !gameHidden;
	}

	/**
	 * Итоговая видимость компактного HUD этапа 1.9: скрыт всегда (см.
	 * {@link #shouldShowCompactStageHud()}), а также при скрытом gameplay-HUD или без данных сервера.
	 * Присутствие метода фиксирует контракт для будущего включения, а не текущую отрисовку.
	 */
	public static boolean compactStageHudVisible(boolean gameHidden, boolean hasData) {
		return shouldShowCompactStageHud() && !gameHidden && hasData;
	}

	// ------------------------------------------------------------------
	// Мелкие реальные размеры
	// ------------------------------------------------------------------

	/** Логический шаг строки (HEIGHT + ROW_GAP). */
	public static int rowStepLogical() {
		return HEIGHT + ROW_GAP;
	}

	/** Реальная высота строки. */
	public static int rowHeightReal() {
		return Math.round(HEIGHT * SCALE);
	}

	/** Реальный зазор строк. */
	public static int rowGapReal() {
		return Math.round(ROW_GAP * SCALE);
	}

	/** Реальная толщина мини-полоски (не меньше 1 пикселя). */
	public static int barHeightReal() {
		return Math.max(1, Math.round(BAR_HEIGHT * SCALE));
	}

	/** Реальный размер иконки. */
	public static int iconReal() {
		return Math.round(ICON * SCALE);
	}

	// ------------------------------------------------------------------
	// Цвет/альфа
	// ------------------------------------------------------------------

	/** Зажимает альфу 0..255. */
	public static int clampAlpha(int alpha) {
		return Math.max(0, Math.min(255, alpha));
	}

	/** Заменяет альфу цвета, сохраняя RGB. */
	public static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (clampAlpha(alpha) << 24);
	}

	/** Масштабирует альфу цвета на коэффициент 0..1 (для плавного появления/исчезновения). */
	public static int fade(int color, double alpha) {
		int base = (color >>> 24) & 0xFF;
		return withAlpha(color, (int) Math.round(base * Math.max(0.0, Math.min(1.0, alpha))));
	}

	// ------------------------------------------------------------------
	// Перенос/обрезка текста
	// ------------------------------------------------------------------

	/** Обрезает строку по ширине, добавляя многоточие, если не влезает. */
	public static String ellipsize(String text, int maxWidth, WidthMeasure measure) {
		if (text == null || text.isEmpty() || maxWidth <= 0) {
			return "";
		}
		if (measure.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		int room = Math.max(0, maxWidth - measure.width(ellipsis));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			String candidate = sb.toString() + text.charAt(i);
			if (measure.width(candidate) > room) {
				break;
			}
			sb.append(text.charAt(i));
		}
		return sb + ellipsis;
	}

	/**
	 * Переносит длинную строку максимум на {@link #GOAL_MAX_LINES} строки по {@code maxWidth}.
	 * Лишнее заканчивается многоточием. Возвращает 1 или 2 строки.
	 */
	public static String[] wrapGoal(String text, int maxWidth, WidthMeasure measure) {
		String safe = text == null ? "" : text;
		if (maxWidth <= 0 || safe.isEmpty()) {
			return new String[] { safe };
		}
		if (measure.width(safe) <= maxWidth) {
			return new String[] { safe };
		}
		String[] words = safe.split(" ");
		StringBuilder first = new StringBuilder();
		int consumed = 0;
		for (int i = 0; i < words.length; i++) {
			String candidate = first.length() == 0 ? words[i] : first + " " + words[i];
			if (measure.width(candidate) <= maxWidth) {
				first.setLength(0);
				first.append(candidate);
				consumed = i + 1;
			} else {
				break;
			}
		}
		if (first.length() == 0) {
			// Даже одно слово не влезает — одна обрезанная строка.
			return new String[] { ellipsize(safe, maxWidth, measure) };
		}
		if (consumed >= words.length) {
			return new String[] { first.toString() };
		}
		StringBuilder rest = new StringBuilder();
		for (int i = consumed; i < words.length; i++) {
			if (rest.length() > 0) {
				rest.append(' ');
			}
			rest.append(words[i]);
		}
		return new String[] { first.toString(), ellipsize(rest.toString(), maxWidth, measure) };
	}
}

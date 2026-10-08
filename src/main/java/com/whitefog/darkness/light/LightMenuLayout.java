package com.whitefog.darkness.light;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Чистая (без Minecraft) раскладка компактной панели источника света (тикет поверх 1.7).
 *
 * <p>Порт подтверждённой sandbox-модели {@code tests/eternal_darkness/portable_light/PortableLightLayout}
 * в common: панель по содержимому, ограничена доступной шириной/высотой; на узких экранах кнопки
 * переходят в вертикальный компактный режим, строки обрезаются по внутренней ширине. Любой
 * прямоугольник целиком внутри панели ({@code 0 <= x}, {@code right <= panelWidth}). Ширина текста
 * задаётся функцией измерения (в реальном экране — {@code Font::width}), поэтому «never overflow»
 * проверяется по фактическому шрифту, а не только по средней оценке.</p>
 *
 * <p>Это НЕ пиксельный рендер: доказательство раскладки — независимый sandbox, а этот класс лишь
 * переносит ту же геометрию в основной код.</p>
 */
public final class LightMenuLayout {
	/** Внутренний отступ панели. */
	public static final int PAD = 6;
	/** Высота строки текста. */
	public static final int LINE = 10;
	/** Зазор между кнопками/блоками. */
	public static final int GAP = 4;
	/** Высота кнопки. */
	public static final int BUTTON_H = 12;
	/** Горизонтальный внутренний отступ кнопки с КАЖДОЙ стороны (текст не впритык к краю). */
	public static final int BUTTON_H_PAD = 5;
	/** Минимальная ширина панели. */
	public static final int MIN_PANEL_WIDTH = 84;
	/** Ниже этой ширины кнопки по умолчанию вертикальные. */
	public static final int HORIZONTAL_BUTTONS_MIN_WIDTH = 190;

	/** Полный заголовок. */
	public static final String TITLE = "Источник света";
	/** Компактный заголовок. */
	public static final String TITLE_COMPACT = "Свет";
	/** Подписи кнопок в широкой форме. */
	public static final String[] WIDE_LABELS = { "Заправить", "Потушить", "Зажечь" };
	/** Подписи кнопок в компактной форме. */
	public static final String[] COMPACT_LABELS = { "Зар.", "Тушить", "Зажечь" };

	private LightMenuLayout() {
	}

	/** Прямоугольник в пикселях панели (x,y — левый верхний угол). */
	public record Rect(int x, int y, int w, int h) {
		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}
	}

	/** Строка панели с подписью. */
	public record Row(String label, Rect rect) {
	}

	/** Кнопка панели с подписью и состоянием. */
	public record Button(String label, Rect rect, boolean enabled) {
	}

	/** Итоговая раскладка панели. */
	public record Layout(int panelWidth, int panelHeight, boolean compact, List<Row> rows, List<Button> buttons) {
	}

	/** Детерминированная оценка ширины текста (6 px на символ) — для тестов/не-клиента. */
	public static int textWidth(String text) {
		return text == null ? 0 : text.length() * 6;
	}

	/** Обрезает строку так, чтобы её ширина не превышала {@code maxWidth} (добавляет «…»). */
	public static String fitText(String text, int maxWidth, ToIntFunction<String> measure) {
		if (text == null || maxWidth <= 0) {
			return "";
		}
		if (measure.applyAsInt(text) <= maxWidth) {
			return text;
		}
		int ellipsis = measure.applyAsInt("…");
		int room = Math.max(0, maxWidth - ellipsis);
		StringBuilder builder = new StringBuilder();
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			int w = measure.applyAsInt(ch);
			if (width + w > room) {
				break;
			}
			width += w;
			builder.append(ch);
		}
		return builder + "…";
	}

	/**
	 * Считает раскладку. Гарантия: любой прямоугольник целиком внутри панели, панель не превышает
	 * доступную ширину. Горизонтальные кнопки — только если подписи реально помещаются.
	 *
	 * @param availableWidth  доступная ширина (например, ширина окна в GUI-координатах)
	 * @param availableHeight доступная высота
	 * @param measure         измерение ширины текста (в экране — {@code font::width})
	 * @param statusLine      строка состояния источника
	 * @param fuelLine        строка остатка (человекочитаемая длительность)
	 * @param timeLine        строка часов вечной ночи
	 * @param b0,b1,b2        доступность кнопок «Заправить»/«Потушить»/«Зажечь»
	 */
	public static Layout compute(int availableWidth, int availableHeight, ToIntFunction<String> measure,
			String statusLine, String fuelLine, String timeLine, boolean b0, boolean b1, boolean b2) {
		int safeWidth = Math.max(1, availableWidth);
		int labelsWideTotal = PAD * 2;
		for (int i = 0; i < WIDE_LABELS.length; i++) {
			labelsWideTotal += measure.applyAsInt(WIDE_LABELS[i]) + BUTTON_H_PAD * 2;
			if (i > 0) {
				labelsWideTotal += GAP;
			}
		}
		boolean compact = safeWidth < HORIZONTAL_BUTTONS_MIN_WIDTH || labelsWideTotal > safeWidth;
		String title = compact ? TITLE_COMPACT : TITLE;
		String[] labels = compact ? COMPACT_LABELS : WIDE_LABELS;

		String[] rowTexts = { title, statusLine, fuelLine, timeLine };
		int textContent = 0;
		for (String text : rowTexts) {
			textContent = Math.max(textContent, measure.applyAsInt(text));
		}

		// Минимальная ширина кнопки = текст + внутренние отступы с обеих сторон.
		int buttonsTextWidth = 0;
		int buttonsTextMax = 0;
		for (String label : labels) {
			int w = measure.applyAsInt(label) + BUTTON_H_PAD * 2;
			buttonsTextWidth += w;
			buttonsTextMax = Math.max(buttonsTextMax, w);
		}

		int needed;
		if (!compact) {
			needed = Math.max(textContent, buttonsTextWidth + GAP * (labels.length - 1)) + PAD * 2;
		} else {
			needed = Math.max(textContent, buttonsTextMax) + PAD * 2;
		}
		int panelWidth = Math.min(Math.max(needed, MIN_PANEL_WIDTH), safeWidth);
		if (safeWidth < MIN_PANEL_WIDTH) {
			panelWidth = safeWidth;
		}
		int inner = Math.max(0, panelWidth - PAD * 2);

		List<Row> rows = new ArrayList<>();
		int y = PAD;
		for (String text : rowTexts) {
			String fitted = fitText(text, inner, measure);
			rows.add(new Row(fitted, new Rect(PAD, y, Math.min(measure.applyAsInt(fitted), inner), LINE)));
			y += LINE;
		}

		int buttonsStart = y + GAP;
		List<Button> buttons = new ArrayList<>();
		boolean[] enabled = { b0, b1, b2 };
		int buttonsHeight;
		if (!compact) {
			int limit = PAD + inner;
			int bx = PAD;
			for (int i = 0; i < labels.length; i++) {
				int w = measure.applyAsInt(labels[i]) + BUTTON_H_PAD * 2;
				if (bx + w > limit) {
					w = Math.max(0, limit - bx);
				}
				buttons.add(new Button(labels[i], new Rect(bx, buttonsStart, w, BUTTON_H), enabled[i]));
				bx += w + GAP;
			}
			buttonsHeight = BUTTON_H;
		} else {
			int by = buttonsStart;
			for (int i = 0; i < labels.length; i++) {
				buttons.add(new Button(labels[i], new Rect(PAD, by, inner, BUTTON_H), enabled[i]));
				by += BUTTON_H + GAP;
			}
			buttonsHeight = labels.length * BUTTON_H + (labels.length - 1) * GAP;
		}

		int panelHeight = buttonsStart + buttonsHeight + PAD;
		return new Layout(panelWidth, panelHeight, compact, rows, buttons);
	}
}

package com.whitefog.tests.portablelight;

import java.util.ArrayList;
import java.util.List;

/**
 * Чистая (без Minecraft) модель раскладки меню/HUD переносного света.
 *
 * <p>Формализует требование «фиксированные границы title/status/buttons/fuel/time без переполнения
 * на узких ширинах, русские строки». Ширина текста считается детерминированно
 * ({@code 6 px/символ}, как средний пиксель шрифта MC), поэтому раскладку можно проверить
 * независимо от клиентского рендера. Это НЕ пиксельный proof.</p>
 */
public final class PortableLightLayout {
	private PortableLightLayout() {
	}

	// ------------------------------------------------------------------
	// Константы стиля (§10 AGENTS.md: единый компактный панельный стиль)
	// ------------------------------------------------------------------

	/** Внутренний отступ панели. */
	public static final int PAD = 6;
	/** Высота строки текста. */
	public static final int LINE = 10;
	/** Зазор между кнопками/блоками. */
	public static final int GAP = 4;
	/** Высота кнопки. */
	public static final int BUTTON_H = 12;
	/** Минимальная ширина панели. */
	public static final int MIN_PANEL_WIDTH = 84;
	/** До этой ширины кнопки раскладываются горизонтально; ниже — вертикально и компактные подписи. */
	public static final int HORIZONTAL_BUTTONS_MIN_WIDTH = 190;

	/** Заголовок (полная форма). */
	public static final String TITLE = "Переносной свет";
	/** Заголовок (компактная форма для узкой панели). */
	public static final String TITLE_COMPACT = "Свет";
	/** Подписи кнопок в широкой форме. */
	public static final String[] WIDE_LABELS = { "Заправить", "Потушить", "Зажечь" };
	/** Подписи кнопок в компактной форме. */
	public static final String[] COMPACT_LABELS = { "Зар.", "Тушить", "Зажечь" };

	/** Строка «нет данных» для отсутствующего/повреждённого переносного света. */
	public static final String STATUS_ABSENT = "Источник: не в руке";
	/** Строка повреждённого стека. */
	public static final String STATUS_CORRUPT = "Источник: повреждён";

	// ------------------------------------------------------------------
	// Геометрия
	// ------------------------------------------------------------------

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

	/** Детерминированная ширина текста (6 px на символ). */
	public static int textWidth(String text) {
		return text == null ? 0 : text.length() * 6;
	}

	/** Обрезает строку так, чтобы её ширина не превышала {@code maxWidth} (добавляет «…»). */
	public static String fitText(String text, int maxWidth) {
		if (text == null || textWidth(text) <= maxWidth) {
			return text == null ? "" : text;
		}
		int maxChars = Math.max(1, maxWidth / 6);
		if (maxChars <= 1) {
			return "…";
		}
		return text.substring(0, maxChars - 1) + "…";
	}

	/** Представление состояния переносного света для раскладки. */
	public record View(String statusLine, String fuelLine, String timeLine,
			boolean zapravit, boolean potushit, boolean zazhech) {
		/** Строит представление из состояния модели. */
		public static View from(PortableLightModel.State s, boolean fuelAvailable, int totalClockTicks) {
			String status;
			if (!s.present()) {
				status = STATUS_ABSENT;
			} else if (s.corrupt()) {
				status = STATUS_CORRUPT;
			} else {
				status = s.kind().displayName + (s.lit() ? " горит" : " погас");
			}
			String fuel = "Топливо: " + (s.present() && !s.corrupt()
					? PortableLightModel.formatDuration(s.remaining()) : "нет топлива");
			String time = PortableLightModel.nightClockLabel(totalClockTicks);
			return new View(status, fuel, time,
					PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAPRAVIT, s, fuelAvailable),
					PortableLightModel.buttonEnabled(PortableLightModel.Button.POTUSHIT, s, fuelAvailable),
					PortableLightModel.buttonEnabled(PortableLightModel.Button.ZAZHECH, s, fuelAvailable));
		}
	}

	/**
	 * Считает раскладку. Гарантия: любой прямоугольник целиком внутри панели
	 * ({@code 0 <= x} и {@code right <= panelWidth}), а сама панель не превышает доступную ширину.
	 * Горизонтальные кнопки — только при {@code availableWidth >= HORIZONTAL_BUTTONS_MIN_WIDTH}.
	 */
	public static Layout compute(int availableWidth, int availableHeight, View view) {
		boolean compact = availableWidth < HORIZONTAL_BUTTONS_MIN_WIDTH;
		String title = compact ? TITLE_COMPACT : TITLE;
		String[] labels = compact ? COMPACT_LABELS : WIDE_LABELS;

		String[] rowTexts = { title, view.statusLine(), view.fuelLine(), view.timeLine() };
		int textContent = 0;
		for (String text : rowTexts) {
			textContent = Math.max(textContent, textWidth(text));
		}

		int buttonsTextWidth = 0;
		int buttonsTextMax = 0;
		for (String label : labels) {
			buttonsTextWidth += textWidth(label);
			buttonsTextMax = Math.max(buttonsTextMax, textWidth(label));
		}

		boolean horizontal = !compact;
		int needed;
		if (horizontal) {
			// Горизонтально кнопки получают ровно свою ширину по подписи + зазоры.
			needed = Math.max(textContent, buttonsTextWidth + GAP * 2) + PAD * 2;
		} else {
			needed = Math.max(textContent, buttonsTextMax) + PAD * 2;
		}
		int panelWidth = Math.min(Math.max(needed, MIN_PANEL_WIDTH), Math.max(1, availableWidth));
		if (availableWidth < MIN_PANEL_WIDTH) {
			panelWidth = Math.max(1, availableWidth);
		}
		int inner = Math.max(0, panelWidth - PAD * 2);

		List<Row> rows = new ArrayList<>();
		int y = PAD;
		for (String text : rowTexts) {
			String fitted = fitText(text, inner);
			int w = Math.min(textWidth(fitted), inner);
			rows.add(new Row(fitted, new Rect(PAD, y, w, LINE)));
			y += LINE;
		}

		int buttonsStart = y + GAP;
		List<Button> buttons = new ArrayList<>();
		boolean[] enabled = { view.zapravit(), view.potushit(), view.zazhech() };
		int buttonsHeight;
		if (horizontal) {
			int limit = PAD + inner;
			int bx = PAD;
			for (int i = 0; i < labels.length; i++) {
				int w = textWidth(labels[i]);
				if (bx + w > limit) {
					w = Math.max(0, limit - bx); // жёсткая гарантия: не выходим за панель
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

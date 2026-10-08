package com.whitefog.tests.lightfix;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Чистая модель раскладки панели источника света — зеркало {@code LightMenuLayout}
 * ПОСЛЕ добавления горизонтального внутреннего отступа кнопок ({@link #BUTTON_H_PAD}).
 *
 * <p>Доказывает, что ширина каждой кнопки &ge; ширины текста + отступы с обеих сторон, что
 * прямоугольники не выходят за панель и не пересекаются, а панель не шире доступной области.
 * {@link #computeOld} (без отступа) включён для контраста: там кнопка «впритык» к тексту.</p>
 *
 * <p>Logic-only: это НЕ пиксельный рендер.</p>
 */
public final class LayoutModel {
	public static final int PAD = 6;
	public static final int LINE = 10;
	public static final int GAP = 4;
	public static final int BUTTON_H = 12;
	public static final int BUTTON_H_PAD = 5;
	public static final int MIN_PANEL_WIDTH = 84;
	public static final int HORIZONTAL_BUTTONS_MIN_WIDTH = 190;

	public static final String TITLE = "Источник света";
	public static final String TITLE_COMPACT = "Свет";
	public static final String[] WIDE_LABELS = { "Заправить", "Потушить", "Зажечь" };
	public static final String[] COMPACT_LABELS = { "Зар.", "Тушить", "Зажечь" };

	private LayoutModel() {
	}

	public record Rect(int x, int y, int w, int h) {
		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}
	}

	public record Button(String label, Rect rect, boolean enabled) {
	}

	public record Layout(int panelWidth, int panelHeight, boolean compact, List<Button> buttons) {
	}

	public static int textWidth(String text) {
		return text == null ? 0 : text.length() * 6;
	}

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

	/** Новая раскладка: кнопка = текст + 2*{@link #BUTTON_H_PAD}. */
	public static Layout computeNew(int availableWidth, ToIntFunction<String> measure, String statusLine,
			String fuelLine, String timeLine, boolean b0, boolean b1, boolean b2) {
		return compute(availableWidth, measure, statusLine, fuelLine, timeLine, b0, b1, b2, BUTTON_H_PAD);
	}

	/** Старая раскладка: кнопка = текст (дефект «кнопка короче текста»). */
	public static Layout computeOld(int availableWidth, ToIntFunction<String> measure, String statusLine,
			String fuelLine, String timeLine, boolean b0, boolean b1, boolean b2) {
		return compute(availableWidth, measure, statusLine, fuelLine, timeLine, b0, b1, b2, 0);
	}

	private static Layout compute(int availableWidth, ToIntFunction<String> measure, String statusLine,
			String fuelLine, String timeLine, boolean b0, boolean b1, boolean b2, int buttonPad) {
		int safeWidth = Math.max(1, availableWidth);
		int labelsWideTotal = PAD * 2;
		for (int i = 0; i < WIDE_LABELS.length; i++) {
			labelsWideTotal += measure.applyAsInt(WIDE_LABELS[i]) + buttonPad * 2;
			if (i > 0) {
				labelsWideTotal += GAP;
			}
		}
		boolean compact = safeWidth < HORIZONTAL_BUTTONS_MIN_WIDTH || labelsWideTotal > safeWidth;
		String[] labels = compact ? COMPACT_LABELS : WIDE_LABELS;

		int textContent = 0;
		for (String text : new String[] { compact ? TITLE_COMPACT : TITLE, statusLine, fuelLine, timeLine }) {
			textContent = Math.max(textContent, measure.applyAsInt(text));
		}

		int sum = 0;
		int max = 0;
		for (String label : labels) {
			int w = measure.applyAsInt(label) + buttonPad * 2;
			sum += w;
			max = Math.max(max, w);
		}
		int needed = compact
				? Math.max(textContent, max) + PAD * 2
				: Math.max(textContent, sum + GAP * (labels.length - 1)) + PAD * 2;
		int panelWidth = Math.min(Math.max(needed, MIN_PANEL_WIDTH), safeWidth);
		if (safeWidth < MIN_PANEL_WIDTH) {
			panelWidth = safeWidth;
		}
		int inner = Math.max(0, panelWidth - PAD * 2);

		int buttonsStart = PAD + LINE * 4 + GAP;
		List<Button> buttons = new ArrayList<>();
		boolean[] enabled = { b0, b1, b2 };
		int buttonsHeight;
		if (!compact) {
			int bx = PAD;
			int limit = PAD + inner;
			for (int i = 0; i < labels.length; i++) {
				int w = measure.applyAsInt(labels[i]) + buttonPad * 2;
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
		return new Layout(panelWidth, buttonsStart + buttonsHeight + PAD, compact, buttons);
	}

	/** Кнопка, в которой текст гарантированно помещается (для нового layout: label + 2*PAD). */
	public static boolean labelFits(Button button, ToIntFunction<String> measure, int pad) {
		return button.rect().w() >= measure.applyAsInt(button.label()) + pad * 2;
	}
}

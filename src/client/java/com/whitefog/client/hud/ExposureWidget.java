package com.whitefog.client.hud;

import com.whitefog.client.ClientDarknessState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Виджет тонкой полоски exposure (этап 1.9): градиентная мини-полоска плюс подпись
 * {@code Тьма: E%}. Строка 1 статус-панели, рисуется как ребёнок {@link WhiteFogHud} в логических
 * координатах. Значение полоски сглаживается тем же {@link DarkHudLayout#approach}, что и альфа
 * (frame-rate independent).
 */
@Environment(EnvType.CLIENT)
final class ExposureWidget implements HudWidget {
	/** Индекс строки «Тьма». */
	static final int ROW_DARK = 1;

	private static final String KEY_DARK = "hud.white_fog.dark";

	private final ClientDarknessState state;
	private boolean visible;
	private double alpha;
	private double barValue;
	private int panelWidthLogical;
	private int panelHeightLogical;

	ExposureWidget(ClientDarknessState state) {
		this.state = state;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public void updateAnimations(double dt) {
		this.alpha = DarkHudLayout.approach(this.alpha, this.visible ? 1.0 : 0.0,
				DarkHudLayout.APPROACH_SPEED * dt);
		double target = Math.max(0, Math.min(100, this.state.lightExposure())) / 100.0;
		this.barValue = DarkHudLayout.approach(this.barValue, target, DarkHudLayout.APPROACH_SPEED * dt);
	}

	@Override
	public int contentWidth(Font font) {
		return font.width(darkComponent());
	}

	@Override
	public void setPanelSize(int widthLogical, int heightLogical) {
		this.panelWidthLogical = widthLogical;
		this.panelHeightLogical = heightLogical;
	}

	@Override
	public void renderBackground(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0) {
			return;
		}
		int x = DarkHudLayout.PAD;
		int width = Math.max(1, this.panelWidthLogical - DarkHudLayout.PAD * 2);
		int y = barY();
		// Трек полоски — затемнённый фон.
		graphics.fill(x, y, x + width, y + DarkHudLayout.BAR_HEIGHT,
				DarkHudLayout.fade(0xFF000000, this.alpha * 0.45));
	}

	@Override
	public void renderIcons(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0) {
			return;
		}
		int x = DarkHudLayout.PAD;
		int width = Math.max(1, this.panelWidthLogical - DarkHudLayout.PAD * 2);
		int filled = (int) Math.round(width * this.barValue);
		if (filled <= 0) {
			return;
		}
		int y = barY();
		graphics.fillGradient(x, y, x + filled, y + DarkHudLayout.BAR_HEIGHT,
				DarkHudLayout.fade(DarkHudLayout.COLOR_ACCENT, this.alpha),
				DarkHudLayout.fade(DarkHudLayout.COLOR_DANGER, this.alpha));
	}

	@Override
	public void renderText(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0) {
			return;
		}
		int textX = DarkHudLayout.PAD + DarkHudLayout.ICON + DarkHudLayout.TEXT_GAP;
		graphics.text(font, darkComponent(), textX, textY(font),
				DarkHudLayout.fade(DarkHudLayout.COLOR_TEXT, this.alpha), true);
	}

	private Component darkComponent() {
		return Component.translatable(KEY_DARK, this.state.lightExposure());
	}

	private static int barY() {
		return ROW_DARK * DarkHudLayout.rowStepLogical() + DarkHudLayout.HEIGHT - DarkHudLayout.BAR_HEIGHT - 2;
	}

	private static int textY(Font font) {
		return ROW_DARK * DarkHudLayout.rowStepLogical()
				+ Math.max(0, (DarkHudLayout.HEIGHT - font.lineHeight) / 2);
	}
}

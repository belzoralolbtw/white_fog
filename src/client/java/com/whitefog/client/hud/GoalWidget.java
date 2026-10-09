package com.whitefog.client.hud;

import com.whitefog.client.ClientDarknessState;
import com.whitefog.darkness.goal.DarkGoalService;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Виджет текущей цели игрока (этап 1.9): верхний правый угол экрана.
 *
 * <p>Показывает локализованное имя активной цели (ключ {@code goal.white_fog.<short>}), никогда не
 * сырой ID. Длинный текст переносится максимум на две строки по
 * {@link DarkHudLayout#goalTextMaxLogical()} с многоточием, панель ограничена
 * {@link DarkHudLayout#GOAL_MAX_WIDTH} реальных пикселей. Скрывается на узком экране
 * ({@link DarkHudLayout#goalVisible}).</p>
 */
@Environment(EnvType.CLIENT)
final class GoalWidget implements HudWidget {
	private static final String KEY_PREFIX = "goal.white_fog.";

	private final ClientDarknessState state;
	private boolean visible;
	private double alpha;
	private int panelWidthLogical;
	private int panelHeightLogical;

	GoalWidget(ClientDarknessState state) {
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
	}

	@Override
	public int contentWidth(Font font) {
		int width = 0;
		for (String line : lines(font)) {
			width = Math.max(width, font.width(line));
		}
		return width;
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
		graphics.fill(0, 0, this.panelWidthLogical, this.panelHeightLogical,
				DarkHudLayout.fade(DarkHudLayout.COLOR_BACKGROUND, this.alpha));
		graphics.fill(0, 0, this.panelWidthLogical, 1,
				DarkHudLayout.fade(DarkHudLayout.COLOR_ACCENT, this.alpha * 0.6));
	}

	@Override
	public void renderIcons(GuiGraphicsExtractor graphics, Font font) {
		// У цели нет иконки: стиль задаётся фоном и текстом.
	}

	@Override
	public void renderText(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0) {
			return;
		}
		String[] lines = lines(font);
		// Цель без иконки: текст начинается ровно от PAD (без неиспользуемых ICON/TEXT_GAP),
		// поэтому goalTextX() + ширина строки не выходит за правый край панели.
		int textX = DarkHudLayout.goalTextX();
		for (int i = 0; i < lines.length; i++) {
			int rowTop = i * DarkHudLayout.rowStepLogical();
			int textY = rowTop + Math.max(0, (DarkHudLayout.HEIGHT - font.lineHeight) / 2);
			graphics.text(font, lines[i], textX, textY,
					DarkHudLayout.fade(DarkHudLayout.COLOR_ACCENT, this.alpha), true);
		}
	}

	/** Разрешённые строки цели (1..2) из последнего снимка. */
	String[] lines(Font font) {
		String resolved = goalComponent().getString();
		return DarkHudLayout.wrapGoal(resolved, DarkHudLayout.goalTextMaxLogical(), font::width);
	}

	private Component goalComponent() {
		String id = DarkGoalService.normalizeGoalId(this.state.goalId());
		return Component.translatable(KEY_PREFIX + DarkGoalService.shortName(id));
	}
}

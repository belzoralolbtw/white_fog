package com.whitefog.client.hud;

import com.whitefog.client.ClientDarknessState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Виджет предупреждения о тьме (этап 1.9): одно сообщение под прицелом.
 *
 * <p>Показывается только при {@code exposure >= 75} («Найди свет»), при {@code >= 90} — сильнее
 * («Тьма истощает тебя»). При смене причины действует визуальный cooldown 40 тиков: сообщение не
 * мигает, старое держится до истечения cooldown, затем плавно сменяется новым. Cooldown чисто
 * визуальный и не меняет gameplay (порогов/урона на клиенте нет).</p>
 *
 * <p><b>Не используется (запрос пользователя).</b> Оконный HUD-вариант предупреждения отключён:
 * корень {@link WhiteFogHud} этот виджет больше не обновляет и не рисует (прямоугольник
 * предупреждения не создаётся). Вместо него предупреждения шлются обычным action-bar сообщением
 * через чистую {@link DarkWarningPolicy}. Класс оставлен для возможного будущего включения; пороги
 * берутся из {@link DarkWarningPolicy}, чтобы не расходились.</p>
 */
@Environment(EnvType.CLIENT)
final class WarningWidget implements HudWidget {
	/** Причина отсутствует. */
	static final int REASON_NONE = DarkWarningPolicy.REASON_NONE;
	/** «Найди свет». */
	static final int REASON_FIND_LIGHT = DarkWarningPolicy.REASON_FIND_LIGHT;
	/** «Тьма истощает тебя». */
	static final int REASON_DRAIN = DarkWarningPolicy.REASON_DRAIN;
	/** Порог предупреждения «Найди свет». */
	static final int EXPOSURE_FIND_LIGHT = DarkWarningPolicy.EXPOSURE_FIND_LIGHT;
	/** Порог предупреждения «Тьма истощает тебя». */
	static final int EXPOSURE_DRAIN = DarkWarningPolicy.EXPOSURE_DRAIN;
	/** Визуальный cooldown при смене причины (тиков). */
	static final int COOLDOWN_TICKS = DarkWarningPolicy.COOLDOWN_TICKS;

	private static final String KEY_FIND_LIGHT = "hud.white_fog.warning.find_light";
	private static final String KEY_DRAIN = "hud.white_fog.warning.drain";

	private final ClientDarknessState state;
	private boolean visible;
	private double alpha;
	private int pendingReason = REASON_NONE;
	private int activeReason = REASON_NONE;
	private int cooldownTicks;

	WarningWidget(ClientDarknessState state) {
		this.state = state;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public void updateAnimations(double dt) {
		int target = reasonFor(this.state.lightExposure());
		if (target != this.pendingReason) {
			this.pendingReason = target;
			this.cooldownTicks = COOLDOWN_TICKS;
		}
		if (this.cooldownTicks > 0) {
			this.cooldownTicks -= (int) Math.round(dt * 20.0);
			if (this.cooldownTicks < 0) {
				this.cooldownTicks = 0;
			}
		} else {
			this.activeReason = this.pendingReason;
		}
		double targetAlpha = this.visible && this.activeReason != REASON_NONE ? 1.0 : 0.0;
		this.alpha = DarkHudLayout.approach(this.alpha, targetAlpha, DarkHudLayout.APPROACH_SPEED * dt);
	}

	@Override
	public int contentWidth(Font font) {
		Component component = message();
		return component == null ? 0 : font.width(component);
	}

	@Override
	public void setPanelSize(int widthLogical, int heightLogical) {
		// Предупреждение рисуется не в масштабируемой панели — размеры панели не используются.
	}

	@Override
	public void renderBackground(GuiGraphicsExtractor graphics, Font font) {
		// Фона нет: одна строка под прицелом.
	}

	@Override
	public void renderIcons(GuiGraphicsExtractor graphics, Font font) {
		// Иконок нет.
	}

	@Override
	public void renderText(GuiGraphicsExtractor graphics, Font font) {
		Component component = message();
		if (component == null || this.alpha <= 0.0) {
			return;
		}
		int x = DarkHudLayout.warningX(graphics.guiWidth(), font.width(component));
		int y = DarkHudLayout.warningY(graphics.guiHeight());
		graphics.text(font, component, x, y, DarkHudLayout.fade(DarkHudLayout.COLOR_DANGER, this.alpha), true);
	}

	/** Текущее сообщение по активной причине или {@code null}. */
	private Component message() {
		return switch (this.activeReason) {
			case REASON_FIND_LIGHT -> Component.translatable(KEY_FIND_LIGHT);
			case REASON_DRAIN -> Component.translatable(KEY_DRAIN);
			default -> null;
		};
	}

	/** Причина предупреждения по exposure (пороги серверные, клиент только отображает). */
	static int reasonFor(int exposure) {
		if (exposure >= EXPOSURE_DRAIN) {
			return REASON_DRAIN;
		}
		if (exposure >= EXPOSURE_FIND_LIGHT) {
			return REASON_FIND_LIGHT;
		}
		return REASON_NONE;
	}
}

package com.whitefog.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Контракт виджета компактного HUD (этап 1.9).
 *
 * <p>Явная фазовая архитектура: один {@code beginFrame()} на кадр обновляет анимации
 * ({@link #updateAnimations(double)}), затем виджет рисуется тремя фазами
 * ({@link #renderBackground}, {@link #renderIcons}, {@link #renderText}) — так фон/иконки/текст
 * разных виджетов не перемешиваются и общий стиль соблюдается.</p>
 *
 * <p>Виджеты НЕ регистрируются в Fabric по отдельности: единственная точка входа — корневой
 * {@link WhiteFogHud}. Координатная система — логическая (см. {@link DarkHudLayout}); корень
 * выставляет позу (translate + scale 0.5) и гарантированно её сбрасывает.</p>
 */
interface HudWidget {
	/** Показать/скрыть виджет (влияет на целевую альфу анимации). */
	void setVisible(boolean visible);

	/** Обновить анимации за кадр (dt уже clamp 0..0.05 с). */
	void updateAnimations(double dt);

	/** Логическая ширина содержимого для расчёта общей панели. */
	int contentWidth(Font font);

	/** Сообщает логические размеры панели (для фонового прямоугольника). */
	void setPanelSize(int widthLogical, int heightLogical);

	/** Фаза фона. */
	void renderBackground(GuiGraphicsExtractor graphics, Font font);

	/** Фаза иконок. */
	void renderIcons(GuiGraphicsExtractor graphics, Font font);

	/** Фаза текста. */
	void renderText(GuiGraphicsExtractor graphics, Font font);
}

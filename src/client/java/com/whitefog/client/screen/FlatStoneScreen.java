package com.whitefog.client.screen;

import com.whitefog.content.WhiteFogContent;
import com.whitefog.content.menu.FlatStoneMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Клиентский экран станции «Плоский камень» (этап 1.4).
 *
 * <p>Меню пустое (нет предметных слотов), поэтому экран рисует собственную компактную панель
 * по правилам §10 AGENTS.md и вкладку-заглушку рецептов. Реальная вкладка рецептов и слоты
 * появятся вместе с рецептами инструментов на этапе 3.5.</p>
 *
 * <p>Рендер — через MC 26.2 API {@link GuiGraphicsExtractor} (метод
 * {@code extractRenderState}), как и HUD мода.</p>
 */
public class FlatStoneScreen extends AbstractContainerScreen<FlatStoneMenu> {
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 110;
	private static final int COLOR_PANEL = 0xE0101418;
	private static final int COLOR_BORDER = 0xFF8A97A6;
	private static final int COLOR_TITLE = 0xFFE6ECF2;
	private static final int COLOR_TAB_BG = 0x40283038;
	private static final int COLOR_TAB_TEXT = 0xFF8FA0B0;

	public FlatStoneScreen(FlatStoneMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	/** Регистрирует экран для типа меню (только client source set). */
	public static void register() {
		MenuScreens.register(WhiteFogContent.FLAT_STONE_MENU, FlatStoneScreen::new);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;
		graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, COLOR_PANEL);
		graphics.outline(x, y, this.imageWidth, this.imageHeight, COLOR_BORDER);
		graphics.centeredText(this.font, this.title, x + this.imageWidth / 2, y + 8, COLOR_TITLE);
		// Пустая вкладка рецептов: рецепты инструментов добавляются на этапе 3.5.
		graphics.fill(x + 8, y + 24, x + 74, y + 40, COLOR_TAB_BG);
		graphics.text(this.font, "Рецепты", x + 14, y + 30, COLOR_TAB_TEXT);
		graphics.text(this.font, "Пусто (этап 3.5)", x + 14, y + 52, COLOR_TAB_TEXT);
	}
}

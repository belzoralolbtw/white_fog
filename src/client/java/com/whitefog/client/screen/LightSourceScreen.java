package com.whitefog.client.screen;

import com.whitefog.client.ClientLightState;
import com.whitefog.client.WhiteFogClient;
import com.whitefog.content.WhiteFogContent;
import com.whitefog.content.menu.LightSourceMenu;
import com.whitefog.darkness.light.LightMenuLayout;
import com.whitefog.darkness.light.LightPanelFormat;
import com.whitefog.darkness.light.SourceActionPolicy;
import com.whitefog.network.SourcePanelPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Компактный экран источника света (этап поверх 1.6), открываемый ПКМ на управляемом источнике.
 *
 * <p>Экран — обычный {@code AbstractContainerScreen} без слотов. Раскладка считается
 * {@link LightMenuLayout} по фактической ширине окна и реальному шрифту: панель не выходит за
 * границы экрана, на узких окнах кнопки переходят в вертикальный компактный режим с подписями
 * {@code Зар./Тушить/Зажечь}, строки обрезаются по внутренней ширине. Никакого переполнения.</p>
 *
 * <p>Остаток и ёмкость показываются ТОЛЬКО понятной длительностью ({@link LightPanelFormat#durationWords}),
 * без сырых тиков. Кнопки отправляют ванильный menu-button пакет
 * ({@code handleInventoryButtonClick}); действия выполняет сервер и возвращает
 * {@link SourcePanelPayload}, по которому экран обновляется. «Ghost action» нет.</p>
 */
public class LightSourceScreen extends AbstractContainerScreen<LightSourceMenu> {
	/** Резервный размер образа (реальная панель считается по окну; imageWidth/Height неизменяемы). */
	private static final int FALLBACK_WIDTH = 176;
	private static final int FALLBACK_HEIGHT = 150;
	/** Отступ панели от краёв окна. */
	private static final int SCREEN_MARGIN = 4;

	private static final int COLOR_BG = 0xE0101418;
	private static final int COLOR_BORDER = 0xFF8A97A6;
	private static final int COLOR_TITLE = 0xFFE6ECF2;
	private static final int COLOR_TEXT = 0xFFB9C4D0;
	private static final int COLOR_ACCENT = 0xFFE8B44A;
	private static final int COLOR_MUTED = 0xFF7E8B99;

	private Button refuelButton;
	private Button extinguishButton;
	private Button relightButton;

	private LightMenuLayout.Layout layout;
	private int panelX;
	private int panelY;

	public LightSourceScreen(LightSourceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, FALLBACK_WIDTH, FALLBACK_HEIGHT);
	}

	/** Регистрирует экран для типа меню (только client source set). */
	public static void register() {
		MenuScreens.register(WhiteFogContent.LIGHT_SOURCE_MENU, LightSourceScreen::new);
	}

	@Override
	protected void init() {
		super.init();
		ToIntFunction<String> measure = this.font::width;
		SourcePanelPayload panel = WhiteFogClient.lightState().panel();
		boolean present = panel != null && panel.present();
		boolean managed = present && panel.managedByPost();
		boolean lit = present && panel.lit();
		int remaining = present ? panel.remaining() : 0;
		int capacity = present ? panel.capacity() : 0;

		this.layout = LightMenuLayout.compute(
				Math.max(1, this.width - SCREEN_MARGIN * 2),
				Math.max(1, this.height - SCREEN_MARGIN * 2),
				measure,
				statusLine(panel),
				fuelLine(panel, present),
				clockLabel(),
				present && !managed && remaining < capacity,
				present && !managed && lit,
				present && !managed && !lit && remaining > 0);
		this.panelX = Math.max(0, (this.width - this.layout.panelWidth()) / 2);
		this.panelY = Math.max(0, (this.height - this.layout.panelHeight()) / 2);

		List<LightMenuLayout.Button> buttons = this.layout.buttons();
		this.refuelButton = addButton(buttons.get(0), SourceActionPolicy.ACTION_REFUEL);
		this.extinguishButton = addButton(buttons.get(1), SourceActionPolicy.ACTION_EXTINGUISH);
		this.relightButton = addButton(buttons.get(2), SourceActionPolicy.ACTION_RELIGHT);
		updateButtons();
	}

	private Button addButton(LightMenuLayout.Button spec, int action) {
		// Подпись обрезается по фактической ширине кнопки — гарантия «не выходим за границы».
		String label = LightMenuLayout.fitText(spec.label(), Math.max(1, spec.rect().w()), this.font::width);
		return addRenderableWidget(Button.builder(
						Component.literal(label),
						b -> press(action))
				.bounds(this.panelX + spec.rect().x(), this.panelY + spec.rect().y(),
						Math.max(1, spec.rect().w()), spec.rect().h())
				.build());
	}

	/** Отправляет ванильный menu-button пакет; никаких локальных изменений. */
	private void press(int action) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, action);
		}
	}

	/** Панель рисуется в фоне (до виджетов), чтобы кнопки были поверх. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (this.layout == null) {
			return;
		}
		int x = this.panelX;
		int y = this.panelY;
		graphics.fill(x, y, x + this.layout.panelWidth(), y + this.layout.panelHeight(), COLOR_BG);
		graphics.outline(x, y, this.layout.panelWidth(), this.layout.panelHeight(), COLOR_BORDER);

		SourcePanelPayload panel = WhiteFogClient.lightState().panel();
		String title = this.layout.compact() ? LightMenuLayout.TITLE_COMPACT : LightMenuLayout.TITLE;
		String[] texts = { title, statusLine(panel),
				fuelLine(panel, panel != null && panel.present()), clockLabel() };

		int inner = Math.max(0, this.layout.panelWidth() - LightMenuLayout.PAD * 2);
		for (int i = 0; i < texts.length; i++) {
			String fitted = LightMenuLayout.fitText(texts[i], inner, this.font::width);
			int lineY = y + this.layout.rows().get(i).rect().y();
			int color;
			if (i == 0) {
				color = COLOR_TITLE;
			} else if (i == 1) {
				color = panel != null && panel.lit() ? COLOR_ACCENT : COLOR_TEXT;
			} else if (i == 3) {
				color = COLOR_MUTED;
			} else {
				color = COLOR_TEXT;
			}
			graphics.text(this.font, fitted, x + LightMenuLayout.PAD, lineY, color);
		}
	}

	/** Строка состояния: вид/lit/managed + последний статус действия (без сырых тиков). */
	private static String statusLine(SourcePanelPayload panel) {
		if (panel == null) {
			return "Источник: загрузка…";
		}
		if (!panel.present()) {
			return "Источник: недоступен";
		}
		String status = LightPanelFormat.sourceStatusLine(true, panel.kind(), panel.lit(), panel.managedByPost());
		String message = LightPanelFormat.statusMessage(panel.status());
		return message.isEmpty() ? status : status + " · " + message;
	}

	private static String fuelLine(SourcePanelPayload panel, boolean present) {
		if (panel == null || !present) {
			return LightPanelFormat.fuelLine(false, 0);
		}
		return LightPanelFormat.fuelLine(true, panel.remaining());
	}

	/** Подавляем стандартные подписи контейнера (инвентаря нет) — текст рисуем сами. */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// Намеренно пусто.
	}

	private String clockLabel() {
		if (this.minecraft == null || this.minecraft.level == null) {
			return "Ночь · --:--";
		}
		return LightPanelFormat.nightClockLabel(this.minecraft.level.getOverworldClockTime());
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		SourcePanelPayload panel = WhiteFogClient.lightState().panel();
		boolean present = panel != null && panel.present();
		boolean managed = present && panel.managedByPost();
		boolean lit = present && panel.lit();
		int remaining = present ? panel.remaining() : 0;
		int capacity = present ? panel.capacity() : 0;

		if (this.refuelButton != null) {
			// Наличие топлива в руке проверяет сервер; клиент включает кнопку, если место есть.
			this.refuelButton.active = present && !managed && remaining < capacity;
		}
		if (this.extinguishButton != null) {
			this.extinguishButton.active = present && !managed && lit;
		}
		if (this.relightButton != null) {
			this.relightButton.active = present && !managed && !lit && remaining > 0;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

package com.whitefog.client.hud;

import com.whitefog.WhiteFog;
import com.whitefog.client.ClientLightState;
import com.whitefog.client.WhiteFogClient;
import com.whitefog.darkness.light.LightFuelComponent;
import com.whitefog.darkness.light.LightPanelFormat;
import com.whitefog.darkness.light.PortableLightPolicy;
import com.whitefog.darkness.light.PortableLightService;
import com.whitefog.network.LightRefuelResultPayload;
import com.whitefog.network.LightSourceSnapshotPayload;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Work Panel источников света (этап 1.6 + панель поверх 1.6): компактная панель, показываемая,
 * пока удерживается клавиша {@code G}.
 *
 * <p>Показывает ближайший источник (вид, остаток, время до угасания), состояние переносного света
 * в левой руке, кастомные часы вечной ночи {@code Ночь · HH:MM} (по мировому времени, без
 * вводящего в заблуждение ванильного «дня») и подсказку действия. Клавиша {@code G} дополнительно
 * шлёт быстрый заправку-запрос ближайшего источника (см. {@link WhiteFogClient}).</p>
 *
 * <p><b>Раскладка фиксирована и ограничена.</b> Все геометрические числа — именованные константы.
 * Панель по содержимому, ограничена {@link #MAX_WIDTH} и шириной экрана, стоит в нижнем левом углу
 * над хотбаром. Каждая строка обрезается по внутренней ширине ({@link #fitText}), поэтому текст
 * не выходит за панель на узких окнах; строки не пересекаются.</p>
 */
@Environment(EnvType.CLIENT)
public final class LightWorkPanelHud implements HudElement {
	// Геометрия (§10 AGENTS.md): единый компактный стиль.
	private static final int PAD = 6;
	private static final int LINE = 10;
	private static final int GAP = 2;
	private static final int MIN_WIDTH = 110;
	private static final int MAX_WIDTH = 190;
	private static final int MARGIN = 6;
	/** Отступ снизу, оставляющий место хотбару. */
	private static final int BOTTOM_CLEARANCE = 70;
	private static final int LINES = 6;

	// Палитра (2–3 цвета + акцент).
	private static final int BG = 0xD0101418;
	private static final int BORDER = 0xFF8A97A6;
	private static final int TITLE = 0xFFE6ECF2;
	private static final int TEXT = 0xFFB9C4D0;
	private static final int ACCENT = 0xFFE8B44A;
	private static final int MUTED = 0xFF7E8B99;

	private final ClientLightState state;

	private LightWorkPanelHud(ClientLightState state) {
		this.state = state;
	}

	/** Регистрирует элемент HUD. */
	public static void register(ClientLightState state) {
		HudElementRegistry.addLast(WhiteFog.id("light_work_panel"), new LightWorkPanelHud(state));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (!WhiteFogClient.lightPanelKey().isDown()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;

		String[] lines = buildLines(minecraft, this.state);
		int inner = Math.min(MAX_WIDTH, Math.max(1, graphics.guiWidth() - MARGIN * 2)) - PAD * 2;
		int panelWidth = Math.min(Math.max(contentWidth(font, lines, inner), MIN_WIDTH),
				Math.max(1, graphics.guiWidth() - MARGIN * 2));
		if (graphics.guiWidth() - MARGIN * 2 < MIN_WIDTH) {
			panelWidth = Math.max(1, graphics.guiWidth() - MARGIN * 2);
		}
		int panelHeight = LINE * LINES + PAD * 2 + GAP * (LINES - 1);

		int x = MARGIN;
		int y = Math.max(MARGIN, graphics.guiHeight() - BOTTOM_CLEARANCE - panelHeight);

		graphics.fill(x, y, x + panelWidth, y + panelHeight, BG);
		graphics.outline(x, y, panelWidth, panelHeight, BORDER);

		int innerWidth = panelWidth - PAD * 2;
		int textY = y + PAD;
		int[] colors = { TITLE, MUTED, TEXT, TEXT, MUTED, TEXT };
		for (int i = 0; i < lines.length && i < LINES; i++) {
			String line = fitText(font, lines[i], innerWidth);
			graphics.text(font, line, x + PAD, textY, colors[i]);
			textY += LINE + GAP;
		}
	}

	/** Строит строки панели (ровно {@link #LINES}). */
	private static String[] buildLines(Minecraft minecraft, ClientLightState state) {
		LightSourceSnapshotPayload snapshot = state.snapshot();
		Portable portable = portable(minecraft);

		String sourceLine;
		String fuelLine;
		if (snapshot == null) {
			sourceLine = "Источник: ожидание…";
			fuelLine = "Осталось: —";
		} else {
			// Отображение отделено от действия: валидный ГОРЯЩИЙ переносной свет считается
			// источником для показа, но кнопка G (C2S refuel) по-прежнему работает только с
			// поставленным блоком (см. WhiteFogClient.handleLightPanelKey).
			sourceLine = LightPanelFormat.hudSourceLine(snapshot.present(), snapshot.kind(), snapshot.lit(),
					portable.active, portable.kindOrdinal);
			fuelLine = LightPanelFormat.hudFuelLine(snapshot.present(), snapshot.remaining(),
					portable.active, portable.remaining);
		}

		String clockLine = minecraft.level == null
				? "Ночь · --:--"
				: LightPanelFormat.nightClockLabel(minecraft.level.getOverworldClockTime());
		String hintLine = hintLine(state.lastResult());
		return new String[] { "Работа света", sourceLine, fuelLine, portable.line, clockLine, hintLine };
	}

	/** Снимок переносного света в левой руке для HUD: активность, вид и остаток из компонента. */
	private static final class Portable {
		private boolean active;
		private int kindOrdinal = -1;
		private int remaining;
		private String line = LightPanelFormat.heldLine(false, null);
	}

	/**
	 * Читает переносной свет в левой руке: активность (валидный горящий {@code lit}+запас+{@code count==1}),
	 * вид для строки «в руке» и остаток топлива из item-компонента (для «Осталось:»). Пустой/погасший/
	 * испорченный offhand даёт {@code active=false}, поэтому при отсутствии блока источник — «нет рядом».
	 */
	private static Portable portable(Minecraft minecraft) {
		Portable portable = new Portable();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return portable;
		}
		ItemStack offhand = player.getOffhandItem();
		PortableLightPolicy.Kind kind = PortableLightService.kindForStack(offhand);
		if (kind == null) {
			return portable;
		}
		LightFuelComponent.LightFuel fuel = LightFuelComponent.read(offhand);
		int remaining = PortableLightPolicy.normalize(fuel.remainingTicks(), kind.capacity);
		boolean corrupt = LightFuelComponent.isInvalidChargedStack(offhand, fuel.remainingTicks());
		portable.active = PortableLightPolicy.active(true, corrupt, fuel.lit(), remaining);
		portable.kindOrdinal = kind.ordinal();
		portable.remaining = remaining;
		portable.line = LightPanelFormat.heldLine(true, kind.displayName);
		return portable;
	}

	private static String hintLine(LightRefuelResultPayload result) {
		if (result == null) {
			return "[G] действие · ПКМ — меню";
		}
		return switch (result.status()) {
			case LightRefuelResultPayload.STATUS_OK -> "Готово · [G] действие";
			case LightRefuelResultPayload.STATUS_FULL -> "Топливный запас заполнен";
			case LightRefuelResultPayload.STATUS_NO_FUEL -> "Нет топлива в руке";
			case LightRefuelResultPayload.STATUS_REFUSED -> "Отказано";
			case LightRefuelResultPayload.STATUS_STALE -> "Источник изменился";
			case LightRefuelResultPayload.STATUS_MANAGED_BY_POST -> "Управляется постом";
			default -> "[G] действие · ПКМ — меню";
		};
	}

	/** Максимальная ширина строк с учётом внутренней ширины. */
	private static int contentWidth(Font font, String[] lines, int inner) {
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, Math.min(font.width(line), Math.max(0, inner)));
		}
		return width;
	}

	/** Обрезает строку по ширине (с многоточием), чтобы текст не выходил за панель. */
	static String fitText(Font font, String text, int maxWidth) {
		if (text == null || text.isEmpty() || maxWidth <= 0) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		int ellipsis = font.width("…");
		int room = Math.max(0, maxWidth - ellipsis);
		return font.plainSubstrByWidth(text, room) + "…";
	}
}

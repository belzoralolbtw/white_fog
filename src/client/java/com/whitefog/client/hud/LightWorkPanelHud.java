package com.whitefog.client.hud;

import com.whitefog.client.ClientDarknessState;
import com.whitefog.client.ClientLightState;
import com.whitefog.darkness.light.LightFuelComponent;
import com.whitefog.darkness.light.LightPanelFormat;
import com.whitefog.darkness.light.PortableLightPolicy;
import com.whitefog.darkness.light.PortableLightService;
import com.whitefog.network.LightRefuelResultPayload;
import com.whitefog.network.LightSourceSnapshotPayload;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Work Panel источников света (этап 1.6), теперь — ребёнок единственного корневого
 * {@link WhiteFogHud} (этап 1.9). Отдельный HUD-элемент больше НЕ регистрируется; корень показывает
 * панель ПОСТОЯННО в обычном gameplay (в т.ч. когда клавиша {@code G} не удерживается) и вызывает
 * её фазовые методы. Видимость решает чистая политика {@link DarkHudLayout#workPanelVisible}.
 *
 * <p><b>Единственная нижняя панель.</b> Это единственный нижний блок HUD: компактный статус
 * (Свет/Укрытие/Источник) остаётся выключенным, чтобы не было дублирования. Панель использует общий
 * стиль {@link DarkHudLayout}: ту же палитру, те же логические константы
 * {@link DarkHudLayout#PAD}/{@code HEIGHT}/{@code ROW_GAP}/{@code SCALE}, компактный фон с акцентной
 * верхней кромкой и текст с тенью; корень задаёт размер и масштабируемую позу 0.5.</p>
 *
 * <p><b>Полоска тьмы (запрос пользователя).</b> В панель добавлена тонкая мини-полоска exposure с
 * локализованной подписью {@code Тьма: E%} — строка {@link DarkHudLayout#darknessBarRow()}. Значение
 * берётся из серверного снимка тьмы ({@link ClientDarknessState#lightExposure()}), клиент его не
 * считает; сглаживание и геометрия — общие ({@link DarknessBar}, {@link DarkHudLayout#darknessBarLogicalY()},
 * {@link DarkHudLayout#BAR_HEIGHT}). Полоска не пересекает остальные строки и нижним краем уходит выше
 * зоны хотбара (нижний отступ {@link DarkHudLayout#STATUS_BOTTOM_CLEARANCE}).</p>
 *
 * <p>Содержимое, функциональность и остальные строки сохранены: ближайший источник (вид, остаток,
 * время до угасания), состояние переносного света в левой руке, кастомные часы вечной ночи
 * {@code Ночь · HH:MM} и подсказка действия. Клавиша {@code G} по-прежнему шлёт быстрый запрос
 * заправки ближайшего ПОСТАВЛЕННОГО источника (см. {@code WhiteFogClient}) и НЕ связана с видимостью
 * панели. Каждая строка обрезается по внутренней ширине ({@link #fitText}), поэтому текст не выходит
 * за панель.</p>
 */
@Environment(EnvType.CLIENT)
final class LightWorkPanelHud implements HudWidget {
	/** Число строк панели (заголовок + полоска тьмы + пять информационных строк). */
	private static final int LINES = DarkHudLayout.WORK_PANEL_ROWS;

	/** Ключ локализации подписи полоски тьмы. */
	private static final String KEY_DARK = "hud.white_fog.dark";

	private final ClientLightState state;
	private final ClientDarknessState darknessState;
	private final DarknessBar darknessBar = new DarknessBar();
	private boolean visible;
	private double alpha;
	private int panelWidthLogical;
	private int panelHeightLogical;

	LightWorkPanelHud(ClientLightState state, ClientDarknessState darknessState) {
		this.state = state;
		this.darknessState = darknessState;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public void updateAnimations(double dt) {
		// Общий стиль: плавное появление/исчезновение тем же approach, что и у статуса/цели.
		this.alpha = DarkHudLayout.approach(this.alpha, this.visible ? 1.0 : 0.0,
				DarkHudLayout.APPROACH_SPEED * dt);
		// Полоска тьмы: серверный exposure, сглаживание тем же approach (frame dt).
		this.darknessBar.updateAnimations(this.darknessState.lightExposure(), dt);
	}

	@Override
	public int contentWidth(Font font) {
		String[] lines = buildLines();
		int width = 0;
		for (String line : lines) {
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
		if (this.alpha <= 0.0 || this.panelWidthLogical <= 0 || this.panelHeightLogical <= 0) {
			return;
		}
		graphics.fill(0, 0, this.panelWidthLogical, this.panelHeightLogical,
				DarkHudLayout.fade(DarkHudLayout.COLOR_BACKGROUND, this.alpha));
		// Та же тонкая акцентная верхняя кромка, что и у статуса/цели.
		graphics.fill(0, 0, this.panelWidthLogical, 1,
				DarkHudLayout.fade(DarkHudLayout.COLOR_ACCENT, this.alpha * 0.6));
		// Дорожка полоски тьмы — тонкая, в едином стиле с бывшим статус-виджетом.
		int barX = DarkHudLayout.PAD;
		int barY = DarkHudLayout.darknessBarLogicalY();
		int barWidth = DarkHudLayout.darknessBarTrackWidth(this.panelWidthLogical);
		graphics.fill(barX, barY, barX + barWidth, barY + DarkHudLayout.BAR_HEIGHT,
				DarkHudLayout.fade(0xFF000000, this.alpha * 0.45));
	}

	@Override
	public void renderIcons(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0 || this.panelWidthLogical <= 0) {
			return;
		}
		// Заливка полоски тьмы: градиент accent→danger, общая толщина BAR_HEIGHT.
		int filled = this.darknessBar.filledWidth(this.panelWidthLogical);
		if (filled <= 0) {
			return;
		}
		int barX = DarkHudLayout.PAD;
		int barY = DarkHudLayout.darknessBarLogicalY();
		graphics.fillGradient(barX, barY, barX + filled, barY + DarkHudLayout.BAR_HEIGHT,
				DarkHudLayout.fade(DarkHudLayout.COLOR_ACCENT, this.alpha),
				DarkHudLayout.fade(DarkHudLayout.COLOR_DANGER, this.alpha));
	}

	@Override
	public void renderText(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0 || this.panelWidthLogical <= 0) {
			return;
		}
		String[] lines = buildLines();
		int innerWidth = Math.max(0, this.panelWidthLogical - DarkHudLayout.PAD * 2);
		int textY = Math.max(0, (DarkHudLayout.HEIGHT - font.lineHeight) / 2);
		for (int i = 0; i < lines.length && i < LINES; i++) {
			int color = i == 0 ? DarkHudLayout.COLOR_ACCENT : DarkHudLayout.COLOR_TEXT;
			String line = fitText(font, lines[i], innerWidth);
			graphics.text(font, line, DarkHudLayout.PAD, textY, DarkHudLayout.fade(color, this.alpha), true);
			textY += DarkHudLayout.rowStepLogical();
		}
	}

	/**
	 * Строит строки панели (ровно {@link #LINES}); индекс {@link DarkHudLayout#darknessBarRow()} —
	 * подпись полоски тьмы {@code Тьма: E%}.
	 */
	private String[] buildLines() {
		Minecraft minecraft = Minecraft.getInstance();
		LightSourceSnapshotPayload snapshot = this.state.snapshot();
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

		// Полоска тьмы: подпись из локализации, значение — серверный exposure (клиент не считает).
		String darkLine = Component.translatable(KEY_DARK, this.darknessState.lightExposure()).getString();
		String clockLine = minecraft == null || minecraft.level == null
				? "Ночь · --:--"
				: LightPanelFormat.nightClockLabel(minecraft.level.getOverworldClockTime());
		String hintLine = hintLine(this.state.lastResult());
		return new String[] { "Работа света", darkLine, sourceLine, fuelLine, portable.line, clockLine, hintLine };
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
		LocalPlayer player = minecraft == null ? null : minecraft.player;
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

package com.whitefog.client.hud;

import com.whitefog.client.ClientDarknessState;
import com.whitefog.darkness.light.LightFuelComponent;
import com.whitefog.darkness.light.PortableLightPolicy;
import com.whitefog.darkness.light.PortableLightService;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Виджет световой части статус-панели (этап 1.9): строки {@code Свет: L/15}, {@code Укрытие: да/нет}
 * и источник ({@code Топливо: N с} / {@code В руке: Факел} / {@code Источник не найден}) с настоящей
 * иконкой предмета.
 *
 * <p><b>Багфикс поверх 1.9.</b> Раньше источник брался только из серверного снимка
 * ({@code sourceItemId} — ближайший поставленный источник), поэтому заряженный горящий факел в
 * левой руке не отражался. Теперь {@link HudSourceDisplay} детерминированно объединяет серверный
 * поставленный источник и локальный offhand: поставленный побеждает, иначе отображается валидный
 * горящий переносной свет (семантика один-в-один с {@link PortableLightService}/
 * {@link PortableLightPolicy}, без дублирования формулы). Это ТОЛЬКО отображение: клавиша {@code G}
 * по-прежнему заправляет поставленный блок и не трогает offhand.</p>
 *
 * <p>Живёт в нижней левой зоне, рисуется как ребёнок единственного корневого {@link WhiteFogHud}
 * в логических координатах при позе {@code translate + scale 0.5}. Клиент не считает exposure по
 * картинке.</p>
 */
@Environment(EnvType.CLIENT)
final class LightWidget implements HudWidget {
	/** Индекс строки «Свет». */
	static final int ROW_LIGHT = 0;
	/** Индекс строки «Укрытие». */
	static final int ROW_SHELTER = 2;
	/** Индекс строки источника. */
	static final int ROW_SOURCE = 3;

	private static final String KEY_LIGHT = "hud.white_fog.light";
	private static final String KEY_SHELTER_YES = "hud.white_fog.shelter.yes";
	private static final String KEY_SHELTER_NO = "hud.white_fog.shelter.no";
	private static final String KEY_FUEL = "hud.white_fog.fuel";
	private static final String KEY_SOURCE_NONE = "hud.white_fog.source.none";
	private static final String KEY_SOURCE_HELD = "hud.white_fog.source.held";
	private static final String KEY_KIND_TORCH = "hud.white_fog.source.kind.torch";
	private static final String KEY_KIND_SOUL_TORCH = "hud.white_fog.source.kind.soul_torch";

	private final ClientDarknessState state;
	private boolean visible;
	private double alpha;
	private int panelWidthLogical;
	private int panelHeightLogical;
	private String cachedSourceId;
	private ItemStack cachedSourceStack = ItemStack.EMPTY;

	LightWidget(ClientDarknessState state) {
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
		return Math.max(font.width(lightComponent()), Math.max(font.width(shelterComponent()),
				font.width(sourceComponent())));
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
		// Тонкая акцентная верхняя кромка в едином стиле.
		graphics.fill(0, 0, this.panelWidthLogical, 1,
				DarkHudLayout.fade(DarkHudLayout.COLOR_ACCENT, this.alpha * 0.6));
	}

	@Override
	public void renderIcons(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0) {
			return;
		}
		ItemStack stack = sourceStack();
		if (stack.isEmpty()) {
			return;
		}
		int iconX = DarkHudLayout.PAD;
		int iconY = rowTop(ROW_SOURCE) + (DarkHudLayout.HEIGHT - DarkHudLayout.ICON) / 2;
		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(iconX, iconY);
			graphics.pose().scale(DarkHudLayout.ICON / 16.0f);
			graphics.item(stack, 0, 0);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	@Override
	public void renderText(GuiGraphicsExtractor graphics, Font font) {
		if (this.alpha <= 0.0) {
			return;
		}
		int textX = textX();
		graphics.text(font, lightComponent(), textX, textY(font, ROW_LIGHT),
				DarkHudLayout.fade(DarkHudLayout.COLOR_ACCENT, this.alpha), true);
		graphics.text(font, shelterComponent(), textX, textY(font, ROW_SHELTER),
				DarkHudLayout.fade(DarkHudLayout.COLOR_TEXT, this.alpha), true);
		graphics.text(font, sourceComponent(), textX, textY(font, ROW_SOURCE),
				DarkHudLayout.fade(DarkHudLayout.COLOR_TEXT, this.alpha), true);
	}

	private Component lightComponent() {
		return Component.translatable(KEY_LIGHT, this.state.light());
	}

	private Component shelterComponent() {
		return Component.translatable(this.state.shelter() ? KEY_SHELTER_YES : KEY_SHELTER_NO);
	}

	/**
	 * Строка источника: поставленный источник из серверного снимка → валидный горящий offhand →
	 * «Источник не найден». Отображаемый источник выбирает чистая {@link HudSourceDisplay}; для
	 * offhand показывается локализованное имя вида (не сырой id) и его остаток.
	 */
	private Component sourceComponent() {
		HudSourceDisplay.Resolved resolved = resolveSource();
		if (!resolved.present()) {
			return Component.translatable(KEY_SOURCE_NONE);
		}
		if (resolved.origin() == HudSourceDisplay.Origin.OFFHAND) {
			return Component.translatable(KEY_SOURCE_HELD, kindComponent(resolved.kindOrdinal()),
					resolved.fuelSeconds());
		}
		return Component.translatable(KEY_FUEL, resolved.fuelSeconds());
	}

	/** Локализованное имя вида переносного света (сырые item id пользователю не показываются). */
	private static Component kindComponent(int kindOrdinal) {
		if (kindOrdinal == PortableLightPolicy.Kind.SOUL_TORCH.ordinal()) {
			return Component.translatable(KEY_KIND_SOUL_TORCH);
		}
		return Component.translatable(KEY_KIND_TORCH);
	}

	/**
	 * Объединяет серверный поставленный источник снимка тьмы и локальный offhand. Валидность
	 * offhand берётся ровно из production-семантики переносного света (вид + компонент +
	 * {@link PortableLightPolicy#active}), чтобы не было расхождения с динамическим светом.
	 */
	private HudSourceDisplay.Resolved resolveSource() {
		boolean placedPresent = this.state.sourceItemId() != null && this.state.sourceRemainingTicks() >= 0;
		int placedRemaining = this.state.sourceRemainingTicks();

		boolean offhandValid = false;
		int offhandKind = -1;
		int offhandRemaining = 0;
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		if (player != null) {
			ItemStack offhand = player.getOffhandItem();
			PortableLightPolicy.Kind kind = PortableLightService.kindForStack(offhand);
			if (kind != null) {
				LightFuelComponent.LightFuel fuel = LightFuelComponent.read(offhand);
				int remaining = PortableLightPolicy.normalize(fuel.remainingTicks(), kind.capacity);
				boolean corrupt = LightFuelComponent.isInvalidChargedStack(offhand, fuel.remainingTicks());
				offhandValid = PortableLightPolicy.active(true, corrupt, fuel.lit(), remaining);
				offhandKind = kind.ordinal();
				offhandRemaining = remaining;
			}
		}
		return HudSourceDisplay.resolve(placedPresent, -1, true, placedRemaining, offhandValid, offhandKind,
				offhandRemaining);
	}

	/** Иконка отображаемого источника: поставленный по id снимка, offhand — реальный стек. */
	private ItemStack sourceStack() {
		HudSourceDisplay.Resolved resolved = resolveSource();
		if (resolved.origin() == HudSourceDisplay.Origin.OFFHAND) {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft != null && minecraft.player != null ? minecraft.player.getOffhandItem() : ItemStack.EMPTY;
		}
		if (resolved.origin() != HudSourceDisplay.Origin.PLACED) {
			return ItemStack.EMPTY;
		}
		String id = this.state.sourceItemId();
		if (id == null || id.isEmpty()) {
			this.cachedSourceId = null;
			this.cachedSourceStack = ItemStack.EMPTY;
			return ItemStack.EMPTY;
		}
		if (id.equals(this.cachedSourceId)) {
			return this.cachedSourceStack;
		}
		Identifier identifier = Identifier.tryParse(id);
		ItemStack stack = identifier == null ? ItemStack.EMPTY
				: new ItemStack(BuiltInRegistries.ITEM.getValue(identifier));
		this.cachedSourceId = id;
		this.cachedSourceStack = stack;
		return stack;
	}

	private static int rowTop(int rowIndex) {
		return rowIndex * DarkHudLayout.rowStepLogical();
	}

	private static int textX() {
		return DarkHudLayout.PAD + DarkHudLayout.ICON + DarkHudLayout.TEXT_GAP;
	}

	private static int textY(Font font, int rowIndex) {
		return rowTop(rowIndex) + Math.max(0, (DarkHudLayout.HEIGHT - font.lineHeight) / 2);
	}
}

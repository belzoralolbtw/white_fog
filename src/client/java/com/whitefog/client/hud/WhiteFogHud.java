package com.whitefog.client.hud;

import com.whitefog.WhiteFog;
import com.whitefog.client.ClientPlayerState;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Заглушка HUD мода «Белая Мгла» (этап 1.1).
 *
 * <p>Элемент уже зарегистрирован через Fabric {@link HudElementRegistry} (MC 26.2 API,
 * {@code extractRenderState(GuiGraphicsExtractor, DeltaTracker)}), но на этом этапе ничего
 * не рисует. Точка расширения для будущего HUD: читать {@link ClientPlayerState#snapshot()}
 * и рисовать компактную панель по правилам §10 AGENTS.md (фиксированная зона, единый стиль,
 * дельта-зависимая анимация).</p>
 *
 * <p>HUD живёт только в client source set; common-код на {@code GuiGraphicsExtractor} не ссылается,
 * поэтому dedicated server запускается без клиентского HUD.</p>
 */
public final class WhiteFogHud implements HudElement {
	private final ClientPlayerState state;

	public WhiteFogHud(ClientPlayerState state) {
		this.state = state;
	}

	/** Регистрирует HUD-элемент в корневом слое. */
	public static void register(ClientPlayerState state) {
		HudElementRegistry.addLast(WhiteFog.id("survival_hud"), new WhiteFogHud(state));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		// Заглушка: рисование появится в следующих этапах.
		// Данные доступны через this.state.snapshot(); сейчас намеренно пусто.
	}
}

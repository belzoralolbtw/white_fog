package com.whitefog.client.network;

import com.whitefog.client.ClientLightState;
import com.whitefog.network.LightRefuelResultPayload;
import com.whitefog.network.LightSourceSnapshotPayload;
import com.whitefog.network.SourcePanelPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Клиентские получатели S2C-пакетов источников света (этап 1.6 + панель поверх 1.6).
 *
 * <p>Регистрируются в client initializer ПОСЛЕ регистрации типов payload в common initializer
 * ({@code WhiteFogPayloads.register()}). Обработка выполняется на клиентском потоке.</p>
 */
public final class LightClientNetworking {
	private LightClientNetworking() {
	}

	/** Регистрирует получатели снимка, результата и состояния панели. */
	public static void register(ClientLightState state) {
		ClientPlayNetworking.registerGlobalReceiver(LightSourceSnapshotPayload.TYPE, (payload, context) ->
				context.client().execute(() -> state.updateSnapshot(payload)));
		ClientPlayNetworking.registerGlobalReceiver(LightRefuelResultPayload.TYPE, (payload, context) ->
				context.client().execute(() -> state.updateResult(payload)));
		ClientPlayNetworking.registerGlobalReceiver(SourcePanelPayload.TYPE, (payload, context) ->
				context.client().execute(() -> state.updatePanel(payload)));
	}
}

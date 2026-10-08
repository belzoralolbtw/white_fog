package com.whitefog.client.network;

import com.whitefog.client.ClientDarknessState;
import com.whitefog.network.DarknessSnapshotPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Клиентский получатель S2C-снимков тьмы (этап 1.5).
 *
 * <p>Регистрируется в client initializer, то есть ПОСЛЕ регистрации типа payload в common
 * initializer ({@code WhiteFogPayloads.register()}). Обработка переносится на клиентский поток.</p>
 */
public final class DarknessClientNetworking {
	private DarknessClientNetworking() {
	}

	/** Регистрирует глобальный получатель снимков тьмы. */
	public static void register(ClientDarknessState state) {
		ClientPlayNetworking.registerGlobalReceiver(DarknessSnapshotPayload.TYPE, (payload, context) ->
				context.client().execute(() -> state.update(payload)));
	}
}

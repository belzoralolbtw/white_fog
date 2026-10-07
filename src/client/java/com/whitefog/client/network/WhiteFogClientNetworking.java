package com.whitefog.client.network;

import com.whitefog.client.ClientPlayerState;
import com.whitefog.network.PlayerStateSyncPayload;
import com.whitefog.state.PlayerSurvivalState;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Клиентский получатель S2C-пакетов состояния.
 *
 * <p>Регистрируется в client initializer, то есть ПОСЛЕ регистрации типа payload в common
 * initializer ({@code WhiteFogPayloads.register()}). Обработка переносится на клиентский поток
 * через {@code context.client().execute(...)}.</p>
 */
public final class WhiteFogClientNetworking {
	private WhiteFogClientNetworking() {
	}

	/** Регистрирует глобальный получатель синхронизации состояния. */
	public static void register(ClientPlayerState state) {
		ClientPlayNetworking.registerGlobalReceiver(PlayerStateSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> state.update(PlayerSurvivalState.fromNbt(payload.data()))));
	}
}

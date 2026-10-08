package com.whitefog.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Регистрация типов custom payload.
 *
 * <p>Вызывается из common initializer до регистрации любых получателей — это обязательное условие
 * Fabric Networking API (иначе падение
 * {@code Cannot register handler as no payload type has been registered ...}).</p>
 *
 * <p>Ссылка: Fabric Docs networking + пример регистрации S2C в общем инициализаторе у мода
 * {@code MoriyaShiine/enchancement} ({@code common/src/main/java/moriyashiine/enchancement/common/Enchancement.java}).</p>
 */
public final class WhiteFogPayloads {
	private WhiteFogPayloads() {
	}

	/** Регистрирует клиентские (S2C) типы пакетов РОВНО ОДИН РАЗ. */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(PlayerStateSyncPayload.TYPE, PlayerStateSyncPayload.STREAM_CODEC);
		// Этап 1.5: снимок состояния тьмы. Тип обязан быть зарегистрирован до получателя.
		PayloadTypeRegistry.clientboundPlay().register(DarknessSnapshotPayload.TYPE, DarknessSnapshotPayload.STREAM_CODEC);
	}
}

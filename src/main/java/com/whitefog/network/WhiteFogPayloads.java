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

	/** Регистрирует клиентские (S2C) и серверные (C2S) типы пакетов РОВНО ОДИН РАЗ. */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(PlayerStateSyncPayload.TYPE, PlayerStateSyncPayload.STREAM_CODEC);
		// Этап 1.5: снимок состояния тьмы. Тип обязан быть зарегистрирован до получателя.
		PayloadTypeRegistry.clientboundPlay().register(DarknessSnapshotPayload.TYPE, DarknessSnapshotPayload.STREAM_CODEC);
		// Этап 1.6: снимок ближайшего источника и ответ на refuel (S2C), запрос refuel (C2S).
		PayloadTypeRegistry.clientboundPlay().register(LightSourceSnapshotPayload.TYPE,
				LightSourceSnapshotPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LightRefuelResultPayload.TYPE,
				LightRefuelResultPayload.STREAM_CODEC);
		// Этап поверх 1.6: состояние панели источника (экран по ПКМ). Действия идут ванильным
		// menu-button пакетом (clickMenuButton), результат — этот S2C-снимок. Тип регистрируется
		// в common ДО клиентского получателя.
		PayloadTypeRegistry.clientboundPlay().register(SourcePanelPayload.TYPE, SourcePanelPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LightRefuelPayload.TYPE, LightRefuelPayload.STREAM_CODEC);
	}
}

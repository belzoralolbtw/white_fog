package com.whitefog.network;

import com.whitefog.WhiteFog;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C-пакет снимка состояния тьмы (этап 1.5).
 *
 * <p>Снимок содержит поля: {@code revision, blockLight, lightExposure, safeLightTicks,
 * conditionMilli, shelter, speedRestricted}. Пакет только информирует клиент — права записи
 * он не несёт, скорость и Condition считает сервер.</p>
 *
 * <p>Тип {@code white_fog:darkness_snapshot} регистрируется ровно один раз в common
 * initializer ({@link WhiteFogPayloads}) до регистрации клиентского получателя.</p>
 */
public record DarknessSnapshotPayload(long revision, int blockLight, int lightExposure, int safeLightTicks,
		int conditionMilli, boolean shelter, boolean speedRestricted) implements CustomPacketPayload {
	/** Уникальный идентификатор канала. */
	public static final CustomPacketPayload.Type<DarknessSnapshotPayload> TYPE =
			new CustomPacketPayload.Type<>(WhiteFog.id("darkness_snapshot"));

	/**
	 * Кодек: семь примитивных полей через {@code StreamCodec.composite}. {@code ByteBuf}-кодеки
	 * сужаются до {@code RegistryFriendlyByteBuf} через {@code cast()} (как в
	 * {@link PlayerStateSyncPayload}).
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, DarknessSnapshotPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_LONG, DarknessSnapshotPayload::revision,
					ByteBufCodecs.VAR_INT, DarknessSnapshotPayload::blockLight,
					ByteBufCodecs.VAR_INT, DarknessSnapshotPayload::lightExposure,
					ByteBufCodecs.VAR_INT, DarknessSnapshotPayload::safeLightTicks,
					ByteBufCodecs.VAR_INT, DarknessSnapshotPayload::conditionMilli,
					ByteBufCodecs.BOOL, DarknessSnapshotPayload::shelter,
					ByteBufCodecs.BOOL, DarknessSnapshotPayload::speedRestricted,
					DarknessSnapshotPayload::new)
					.cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package com.whitefog.network;

import com.whitefog.WhiteFog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * S2C-снимок ближайшего поддерживаемого источника для Work Panel (этап 1.6).
 *
 * <p>Содержит {@code present, dimension, pos, sourceUuid, revision, remaining, lit, kind}. Клиент
 * только отображает состояние и использует {@code sourceUuid}/{@code revision}/{@code pos} для
 * отправки {@link LightRefuelPayload}; источником истины остаётся сервер.</p>
 */
public record LightSourceSnapshotPayload(boolean present, Identifier dimension, BlockPos pos, UUID sourceUuid,
		long revision, int remaining, boolean lit, int kind) implements CustomPacketPayload {

	/** Уникальный идентификатор канала. */
	public static final CustomPacketPayload.Type<LightSourceSnapshotPayload> TYPE =
			new CustomPacketPayload.Type<>(WhiteFog.id("light_source_snapshot"));

	/** Кодек: восемь полей через {@code StreamCodec.composite}. */
	public static final StreamCodec<RegistryFriendlyByteBuf, LightSourceSnapshotPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, LightSourceSnapshotPayload::present,
					Identifier.STREAM_CODEC, LightSourceSnapshotPayload::dimension,
					BlockPos.STREAM_CODEC, LightSourceSnapshotPayload::pos,
					UUIDUtil.STREAM_CODEC, LightSourceSnapshotPayload::sourceUuid,
					ByteBufCodecs.VAR_LONG, LightSourceSnapshotPayload::revision,
					ByteBufCodecs.VAR_INT, LightSourceSnapshotPayload::remaining,
					ByteBufCodecs.BOOL, LightSourceSnapshotPayload::lit,
					ByteBufCodecs.VAR_INT, LightSourceSnapshotPayload::kind,
					LightSourceSnapshotPayload::new)
					.cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Пустой снимок (источника рядом нет). */
	public static LightSourceSnapshotPayload absent(Identifier dimension) {
		return new LightSourceSnapshotPayload(false, dimension, BlockPos.ZERO, new UUID(0L, 0L), -1L, 0, false, -1);
	}
}

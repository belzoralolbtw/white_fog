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
 * C2S-запрос операции с источником света (этап 1.6).
 *
 * <p>Поля: {@code dimension, pos, sourceUuid, expectedRevision, requestSequence}. Клиент НЕ посылает
 * количество/длительность топлива — всё определяет сервер. Тип {@code white_fog:light_refuel}
 * регистрируется один раз в common initializer ({@link WhiteFogPayloads}) до получателя.</p>
 */
public record LightRefuelPayload(Identifier dimension, BlockPos pos, UUID sourceUuid, long expectedRevision,
		int requestSequence) implements CustomPacketPayload {

	/** Уникальный идентификатор канала. */
	public static final CustomPacketPayload.Type<LightRefuelPayload> TYPE =
			new CustomPacketPayload.Type<>(WhiteFog.id("light_refuel"));

	/** Кодек: пять полей через {@code StreamCodec.composite}. */
	public static final StreamCodec<RegistryFriendlyByteBuf, LightRefuelPayload> STREAM_CODEC =
			StreamCodec.composite(
					Identifier.STREAM_CODEC, LightRefuelPayload::dimension,
					BlockPos.STREAM_CODEC, LightRefuelPayload::pos,
					UUIDUtil.STREAM_CODEC, LightRefuelPayload::sourceUuid,
					ByteBufCodecs.VAR_LONG, LightRefuelPayload::expectedRevision,
					ByteBufCodecs.VAR_INT, LightRefuelPayload::requestSequence,
					LightRefuelPayload::new)
					.cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

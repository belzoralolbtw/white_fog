package com.whitefog.network;

import com.whitefog.WhiteFog;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C-ответ на {@link LightRefuelPayload}: итог {@code revision/remaining} либо короткая причина
 * отказа (этап 1.6). Тип {@code white_fog:light_refuel_result} регистрируется один раз в common
 * initializer до клиентского получателя.
 */
public record LightRefuelResultPayload(Identifier dimension, BlockPos pos, long revision, int remaining,
		int status) implements CustomPacketPayload {

	/** Коды статуса ответа. */
	public static final int STATUS_OK = 0;
	/** Источник уже полон (переполнение) — точный текст {@code Топливный запас заполнен}. */
	public static final int STATUS_FULL = 1;
	/** Нет подходящего топлива в главной руке и зажигать нечего. */
	public static final int STATUS_NO_FUEL = 2;
	/** Нет прав/вне дистанции/нет LOS. */
	public static final int STATUS_REFUSED = 3;
	/** Устаревшая ревизия/чужой UUID (источник заменён). */
	public static final int STATUS_STALE = 4;
	/** Источник управляется постом (managedByPost) — refuel запрещён. */
	public static final int STATUS_MANAGED_BY_POST = 5;

	/** Уникальный идентификатор канала. */
	public static final CustomPacketPayload.Type<LightRefuelResultPayload> TYPE =
			new CustomPacketPayload.Type<>(WhiteFog.id("light_refuel_result"));

	/** Кодек: пять полей через {@code StreamCodec.composite}. */
	public static final StreamCodec<RegistryFriendlyByteBuf, LightRefuelResultPayload> STREAM_CODEC =
			StreamCodec.composite(
					Identifier.STREAM_CODEC, LightRefuelResultPayload::dimension,
					BlockPos.STREAM_CODEC, LightRefuelResultPayload::pos,
					ByteBufCodecs.VAR_LONG, LightRefuelResultPayload::revision,
					ByteBufCodecs.VAR_INT, LightRefuelResultPayload::remaining,
					ByteBufCodecs.VAR_INT, LightRefuelResultPayload::status,
					LightRefuelResultPayload::new)
					.cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

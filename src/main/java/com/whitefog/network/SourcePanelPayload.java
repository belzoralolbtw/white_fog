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
 * S2C-состояние панели источника света (этап поверх 1.6). Открывает/обновляет клиентский экран
 * источника, который клиент получает по серверному {@code openMenu}, и сообщает статус последнего
 * действия.
 *
 * <p>Поля: {@code present, dimension, pos, sourceUuid, revision, remaining, lit, kind (ordinal),
 * capacity, managedByPost, status}. Клиент только отображает; действия идут ванильным
 * menu-button пакетом и перепроверяются сервером. Тип {@code white_fog:source_panel}
 * регистрируется один раз в common initializer ({@link WhiteFogPayloads}) до клиентского получателя.</p>
 */
public record SourcePanelPayload(boolean present, Identifier dimension, BlockPos pos, UUID sourceUuid,
		long revision, int remaining, boolean lit, int kind, int capacity, boolean managedByPost, int status)
		implements CustomPacketPayload {

	/** Уникальный идентификатор канала. */
	public static final CustomPacketPayload.Type<SourcePanelPayload> TYPE =
			new CustomPacketPayload.Type<>(WhiteFog.id("source_panel"));

	/** Кодек: одиннадцать полей через {@code StreamCodec.composite}. */
	public static final StreamCodec<RegistryFriendlyByteBuf, SourcePanelPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, SourcePanelPayload::present,
					Identifier.STREAM_CODEC, SourcePanelPayload::dimension,
					BlockPos.STREAM_CODEC, SourcePanelPayload::pos,
					UUIDUtil.STREAM_CODEC, SourcePanelPayload::sourceUuid,
					ByteBufCodecs.VAR_LONG, SourcePanelPayload::revision,
					ByteBufCodecs.VAR_INT, SourcePanelPayload::remaining,
					ByteBufCodecs.BOOL, SourcePanelPayload::lit,
					ByteBufCodecs.VAR_INT, SourcePanelPayload::kind,
					ByteBufCodecs.VAR_INT, SourcePanelPayload::capacity,
					ByteBufCodecs.BOOL, SourcePanelPayload::managedByPost,
					ByteBufCodecs.VAR_INT, SourcePanelPayload::status,
					SourcePanelPayload::new)
					.cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Пустая панель (источник исчез/невалиден). */
	public static SourcePanelPayload absent(Identifier dimension, BlockPos pos, int status) {
		return new SourcePanelPayload(false, dimension, pos, new UUID(0L, 0L), -1L, 0, false, -1, 0, false, status);
	}
}

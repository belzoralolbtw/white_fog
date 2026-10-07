package com.whitefog.network;

import com.whitefog.WhiteFog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C-пакет синхронизации состояния выживания игрока (этап 1.1).
 *
 * <p>Полезная нагрузка — NBT-снимок состояния. Использование NBT позволяет переиспользовать
 * {@code PlayerSurvivalState#toNbt()/fromNbt()} и не упираться в лимит {@code StreamCodec.composite}
 * (16 компонентов), не дублируя кодеки для ~19 полей.</p>
 *
 * <p>Тип пакета регистрируется ровно один раз в common initializer ({@link WhiteFogPayloads}),
 * клиентский получатель — в client initializer, то есть после регистрации типа.</p>
 */
public record PlayerStateSyncPayload(CompoundTag data) implements CustomPacketPayload {
	/** Уникальный идентификатор канала. */
	public static final CustomPacketPayload.Type<PlayerStateSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(WhiteFog.id("player_state_sync"));

	/**
	 * Кодек: NBT-компаунд {@code ByteBufCodecs.COMPOUND_TAG} отображается в/из record.
	 * {@code ByteBufCodecs.COMPOUND_TAG} имеет тип {@code StreamCodec<ByteBuf, CompoundTag>},
	 * а {@code cast()} сужает канал буфера до {@code RegistryFriendlyByteBuf}.
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStateSyncPayload> STREAM_CODEC =
			ByteBufCodecs.COMPOUND_TAG
					.map(PlayerStateSyncPayload::new, PlayerStateSyncPayload::data)
					.cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

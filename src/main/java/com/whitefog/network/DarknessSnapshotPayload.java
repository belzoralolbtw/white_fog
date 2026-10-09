package com.whitefog.network;

import com.whitefog.WhiteFog;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C-пакет снимка света/целей (этапы 1.5 и 1.9).
 *
 * <p>Единственный канал снимка HUD. Поля:</p>
 * <pre>
 * {revision, light, exposure, conditionMilli, shelter,
 *  sourceItemId?, sourceRemainingTicks,
 *  goalId, postCount, finalState, finalRemainingTicks, finalFuelTicks, waveRemainingTicks}
 * </pre>
 * <ul>
 *     <li>{@code sourceItemId == null} — источника рядом нет, тогда {@code sourceRemainingTicks = -1};</li>
 *     <li>{@code sourceItemId} — идентификатор vanilla-предмета-источника (например
 *         {@code minecraft:torch}); клиент резолвит его в реальный {@code ItemStack} для иконки;</li>
 *     <li>{@code goalId} — полный id активной цели ({@code white_fog:*}); raw id пользователю не
 *         показывается, клиент переводит его в локализованный текст.</li>
 * </ul>
 *
 * <p>Кодек написан вручную через {@link StreamCodec#of}, потому что {@code StreamCodec.composite}
 * поддерживает не более 12 компонентов, а здесь 13 полей (плюс nullable-строки). Тип
 * {@code white_fog:darkness_snapshot} регистрируется ровно один раз в common initializer
 * ({@link WhiteFogPayloads}) до регистрации получателя.</p>
 */
public record DarknessSnapshotPayload(long revision, int light, int exposure, int conditionMilli, boolean shelter,
		String sourceItemId, int sourceRemainingTicks, String goalId, int postCount, String finalState,
		long finalRemainingTicks, long finalFuelTicks, long waveRemainingTicks) implements CustomPacketPayload {

	/** Уникальный идентификатор канала. */
	public static final CustomPacketPayload.Type<DarknessSnapshotPayload> TYPE =
			new CustomPacketPayload.Type<>(WhiteFog.id("darkness_snapshot"));

	/** Ручной кодек: 13 полей, две nullable-строки кодируются флагом присутствия. */
	public static final StreamCodec<RegistryFriendlyByteBuf, DarknessSnapshotPayload> STREAM_CODEC =
			StreamCodec.of(DarknessSnapshotPayload::encode, DarknessSnapshotPayload::decode);

	private static void encode(RegistryFriendlyByteBuf buf, DarknessSnapshotPayload payload) {
		buf.writeVarLong(payload.revision);
		buf.writeVarInt(payload.light);
		buf.writeVarInt(payload.exposure);
		buf.writeVarInt(payload.conditionMilli);
		buf.writeBoolean(payload.shelter);
		writeNullable(buf, payload.sourceItemId);
		buf.writeVarInt(payload.sourceRemainingTicks);
		writeNullable(buf, payload.goalId);
		buf.writeVarInt(payload.postCount);
		writeNullable(buf, payload.finalState);
		buf.writeVarLong(payload.finalRemainingTicks);
		buf.writeVarLong(payload.finalFuelTicks);
		buf.writeVarLong(payload.waveRemainingTicks);
	}

	private static DarknessSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
		long revision = buf.readVarLong();
		int light = buf.readVarInt();
		int exposure = buf.readVarInt();
		int conditionMilli = buf.readVarInt();
		boolean shelter = buf.readBoolean();
		String sourceItemId = readNullable(buf);
		int sourceRemainingTicks = buf.readVarInt();
		String goalId = readNullable(buf);
		int postCount = buf.readVarInt();
		String finalState = readNullable(buf);
		long finalRemainingTicks = buf.readVarLong();
		long finalFuelTicks = buf.readVarLong();
		long waveRemainingTicks = buf.readVarLong();
		return new DarknessSnapshotPayload(revision, light, exposure, conditionMilli, shelter, sourceItemId,
				sourceRemainingTicks, goalId, postCount, finalState, finalRemainingTicks, finalFuelTicks,
				waveRemainingTicks);
	}

	private static void writeNullable(FriendlyByteBuf buf, String value) {
		buf.writeBoolean(value != null);
		if (value != null) {
			buf.writeUtf(value);
		}
	}

	private static String readNullable(FriendlyByteBuf buf) {
		return buf.readBoolean() ? buf.readUtf() : null;
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

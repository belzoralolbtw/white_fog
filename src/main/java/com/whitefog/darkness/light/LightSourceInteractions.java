package com.whitefog.darkness.light;

import com.whitefog.WhiteFog;
import com.whitefog.network.LightRefuelPayload;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Регистрация серверных интеракций источников света (этап 1.6 + панель поверх 1.6):
 * C2S-получатель {@code white_fog:light_refuel}, ленивая инициализация чанка по
 * {@code ServerChunkEvents.CHUNK_LOAD} и открытие меню источника по ПКМ
 * ({@link BlockEvents#USE_ITEM_ON}/{@link BlockEvents#USE_WITHOUT_ITEM}).
 *
 * <p>Типы payload регистрируются раньше, в common initializer
 * ({@link com.whitefog.network.WhiteFogPayloads}), до регистрации получателей.</p>
 *
 * <p><b>Совместимость.</b> Меню открывается только для управляемых источников
 * ({@code torch}/{@code wall_torch}/{@code soul_torch}/{@code soul_wall_torch}/{@code lantern}/
 * {@code soul_lantern}/{@code campfire}/{@code soul_campfire}). Костёр с предметом в руке
 * оставлен ванильному использованию (еда/лопата/огниво) — меню открывается пустой рукой.
 * Этим не затрагиваются крафт (1.2), разрушение (1.3) и станция/камушек (1.4).</p>
 */
public final class LightSourceInteractions {
	private static boolean registered = false;

	private LightSourceInteractions() {
	}

	/** Регистрирует получатель, событие загрузки чанка и открытие меню. Идемпотентна. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		ServerPlayNetworking.registerGlobalReceiver(LightRefuelPayload.TYPE, (payload, context) ->
				context.server().execute(() -> LightSourceService.handleRefuel(context.player(), payload)));

		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newChunk) -> LightSourceService.onChunkLoad(level, chunk));

		BlockEvents.USE_WITHOUT_ITEM.register(LightSourceInteractions::onUseWithoutItem);
		BlockEvents.USE_ITEM_ON.register(LightSourceInteractions::onUseItemOn);

		WhiteFog.LOGGER.info(
				"White Fog: light source interactions registered (stage 1.6 + source menu, refuel payload + chunk init)");
	}

	private static InteractionResult onUseWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!LightSourceBlocks.isManaged(state)) {
			return null;
		}
		return openSource(level, pos, player);
	}

	private static InteractionResult onUseItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!LightSourceBlocks.isManaged(state)) {
			return null;
		}
		// Костёр с предметом в руке (еда/лопата/огниво) сохраняет ванильное поведение 1.6.
		if (LightSourceBlocks.kind(state) == LightFuelPolicy.SourceKind.CAMPFIRE) {
			return null;
		}
		return openSource(level, pos, player);
	}

	/**
	 * Открывает панель источника. Решение принимается одинаково на клиенте и сервере (по
	 * синхронизированному состоянию блока), мутацию/меню выполняет только сервер; клиентское
	 * предсказание ванильного использования подавляется (SUCCESS), но use-пакет всё равно уходит
	 * серверу (javap 26.2: {@code MultiPlayerGameMode#useItemOn} всегда строит
	 * {@code ServerboundUseItemOnPacket} в predictive action).
	 */
	private static InteractionResult openSource(Level level, BlockPos pos, Player player) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		return LightSourceService.openSourceMenu(serverPlayer, serverLevel, pos)
				? InteractionResult.SUCCESS
				: InteractionResult.PASS;
	}
}

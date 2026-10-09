package com.whitefog.station;

import com.whitefog.WhiteFog;
import com.whitefog.content.WhiteFogContent;
import com.whitefog.content.menu.FlatStoneMenu;

import net.fabricmc.fabric.api.event.player.BlockEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Серверно-авторитетные interaction'ы станции и камушка (этап 1.4).
 *
 * <p>Используются Fabric-хуки {@link BlockEvents#USE_ITEM_ON}/{@link BlockEvents#USE_WITHOUT_ITEM}
 * (без собственных миксинов). Правила:</p>
 * <ul>
 *     <li>обычный ПКМ по {@code flat_stone} (пустая рука) — открыть станцию;</li>
 *     <li>Shift+ПКМ пустой главной рукой — снять станцию за один interaction;</li>
 *     <li>ПКМ по {@code small_stone} любой рукой — подбор (обрабатывается только main-hand);</li>
 *     <li>Shift+ПКМ cobblestone по твёрдой земле — recovery {@code 2 cobblestone -> 1 flat_stone}.</li>
 * </ul>
 *
 * <p>Решение принимается одинаково на клиенте и сервере (по синхронизированному состоянию),
 * мутацию мира выполняет только сервер; клиентское предсказание ванильного взаимодействия
 * подавляется (SUCCESS). «Обычный ПКМ по flat_stone» с предметом в руке (без Shift) тоже
 * открывает станцию — как и требует паспорт; Shift+предмет оставлен ванильному использованию
 * предмета.</p>
 */
public final class FlatStoneInteractions {

	private static boolean registered = false;

	private FlatStoneInteractions() {
	}

	/** Идемпотентная регистрация серверных interaction-хуков. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		BlockEvents.USE_WITHOUT_ITEM.register(FlatStoneInteractions::onUseWithoutItem);
		BlockEvents.USE_ITEM_ON.register(FlatStoneInteractions::onUseItemOn);
		WhiteFog.LOGGER.info("White Fog: flat-stone/small-stone interactions registered (stage 1.4)");
	}

	private static InteractionResult onUseWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		Block block = state.getBlock();
		if (block == WhiteFogContent.FLAT_STONE) {
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			if (player.isShiftKeyDown()) {
				FlatStoneRemoval.remove(serverPlayer, serverLevel, pos);
			} else {
				openStation(serverPlayer, pos);
			}
			return InteractionResult.SUCCESS;
		}
		if (block == WhiteFogContent.SMALL_STONE) {
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			SmallStonePickup.pickup(serverPlayer, serverLevel, pos);
			return InteractionResult.SUCCESS;
		}
		return null;
	}

	private static InteractionResult onUseItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		Block block = state.getBlock();
		if (block == WhiteFogContent.SMALL_STONE) {
			// Обрабатывается только main-hand event (правило паспорта).
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			SmallStonePickup.pickup(serverPlayer, serverLevel, pos);
			return InteractionResult.SUCCESS;
		}
		if (block == WhiteFogContent.FLAT_STONE) {
			if (player.isShiftKeyDown()) {
				// Shift+предмет — ванильное использование предмета (например установка блока сверху).
				return null;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			openStation(serverPlayer, pos);
			return InteractionResult.SUCCESS;
		}
		// Recovery: Shift+ПКМ cobblestone по твёрдой земле (world interaction, не крафт).
		if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
				&& stack.getItem() == Items.COBBLESTONE && RecoveryService.canStart(player, level, pos)) {
			if (!level.isClientSide() && level instanceof ServerLevel serverLevel
					&& player instanceof ServerPlayer serverPlayer) {
				RecoveryService.start(serverPlayer, serverLevel, pos);
			}
			return InteractionResult.SUCCESS;
		}
		return null;
	}

	private static void openStation(ServerPlayer player, BlockPos pos) {
		if (player.isSpectator()) {
			return;
		}
		WhiteFog.LOGGER.info("WHITEFOG_STATION_MENU player={} dimension={} lookPos={} action=open",
				player.getStringUUID(), player.level().dimension().identifier(), pos);
		player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, p) -> new FlatStoneMenu(syncId, inventory),
				Component.literal("Плоский камень")));
	}
}

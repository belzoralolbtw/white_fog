package com.whitefog.station;

import com.whitefog.WhiteFogConfig;
import com.whitefog.content.WhiteFogContent;
import com.whitefog.content.block.entity.FlatStoneBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Снятие станции «Плоский камень» за ОДИН interaction (Shift+ПКМ пустой главной рукой).
 *
 * <p>Серверно-авторитетно и атомарно: проверяем ожидаемый block state и занятость (job/escrow/
 * output по revision-состоянию block entity), затем удаляем блок и выдаём ровно один предмет.
 * Для interaction-pickup block loot ПОДАВЛЕН ({@code removeBlock(pos, false)}) — иначе был бы
 * второй предмет. Занятая станция не снимается и отвечает точным текстом.</p>
 */
public final class FlatStoneRemoval {

	/** Результат попытки снятия. */
	public enum Outcome {
		/** Снята и выдан ровно один предмет. */
		REMOVED,
		/** Занята (job/escrow/output) — отказ с точным текстом. */
		BUSY,
		/** По позиции нет плоского камня (уже снят/другой блок) — второго результата нет. */
		NO_BLOCK
	}

	private FlatStoneRemoval() {
	}

	public static Outcome remove(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return Outcome.NO_BLOCK;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() != WhiteFogContent.FLAT_STONE) {
			return Outcome.NO_BLOCK;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof FlatStoneBlockEntity station && station.isBusy()) {
			player.sendSystemMessage(Component.literal(WhiteFogConfig.MESSAGE_STATION_BUSY), true);
			return Outcome.BUSY;
		}
		// Interaction-pickup: удаляем без block loot и выдаём ровно один предмет.
		// removeBlockEntity до removeBlock исключает любой выброс содержимого (его и нет — проверили).
		level.removeBlockEntity(pos);
		boolean removed = level.removeBlock(pos, false);
		if (!removed) {
			return Outcome.NO_BLOCK;
		}
		StationDropHelper.giveOne(player, level, pos, new ItemStack(WhiteFogContent.FLAT_STONE_ITEM));
		return Outcome.REMOVED;
	}
}

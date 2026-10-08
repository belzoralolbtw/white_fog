package com.whitefog.content.menu;

import com.whitefog.content.WhiteFogContent;
import com.whitefog.darkness.light.LightSourceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Меню источника света, открываемое по ПКМ на torch/lantern/campfire (этап поверх 1.6).
 *
 * <p>Меню пустое (нет предметных слотов). Только на сервере оно хранит цель — {@code pos}/{@code uuid}
 * блока; на клиенте фабрика {@code MenuType} создаёт меню без цели (она там не нужна: кнопки
 * отправляют ванильный menu-button пакет, а сервер привязывает действие к своей цели).</p>
 *
 * <p>Кнопки: {@code Заправить} (id 1, refuel-job 20 тиков), {@code Потушить} (id 2, мгновенно,
 * сохраняет остаток), {@code Зажечь} (id 3, мгновенно, требует остаток &gt; 0). Все действия
 * повторно валидируются сервером ({@link LightSourceService#handleMenuButton}). Каждый тик
 * {@link #broadcastChanges()} обновляет клиентскую панель при изменении состояния (например,
 * завершении заправки).</p>
 */
public class LightSourceMenu extends AbstractContainerMenu {

	/** Цель-позиция (только сервер). */
	private final BlockPos target;
	/** UUID источника (только сервер). */
	private final UUID sourceUuid;
	/** Владелец меню (на сервере — {@code ServerPlayer}). */
	private final Player owner;

	/** Клиентская фабрика: без цели. */
	public LightSourceMenu(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, null, null, null);
	}

	/** Серверная фабрика: с целью открытого источника. */
	public LightSourceMenu(int syncId, Inventory playerInventory, BlockPos target, UUID sourceUuid, Player owner) {
		super(WhiteFogContent.LIGHT_SOURCE_MENU, syncId);
		this.target = target;
		this.sourceUuid = sourceUuid;
		this.owner = owner;
	}

	/** Переносить нечего (слотов нет). */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/** Пустое меню не привязано к блоку; действия сами проверяют дистанцию/права. */
	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	/**
	 * Действие кнопки: выполняется ТОЛЬКО на сервере. Клиент лишь отправил button-пакет;
	 * никакого локального «ghost action» нет.
	 */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.target == null || this.sourceUuid == null || !(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		LightSourceService.handleMenuButton(serverPlayer, level, this.target, this.sourceUuid, id);
		return true;
	}

	/** Каждый серверный тик обновляет клиентскую панель при изменении состояния источника. */
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (this.target != null && this.sourceUuid != null && this.owner instanceof ServerPlayer serverPlayer
				&& serverPlayer.level() instanceof ServerLevel level) {
			LightSourceService.tickOpenMenu(serverPlayer, level, this.target, this.sourceUuid);
		}
	}
}

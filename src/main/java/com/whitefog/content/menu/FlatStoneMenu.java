package com.whitefog.content.menu;

import com.whitefog.content.WhiteFogContent;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Пустое меню станции «Плоский камень» (этап 1.4).
 *
 * <p>У станции нет предметных слотов в этом этапе: BlockItem-паспорт и job-схема закладываются
 * заранее, а рецепты инструментов и реальные слоты появятся на этапе 3.5. Меню корректно
 * зарегистрировано в {@code BuiltInRegistries.MENU} и открывается сервером по ПКМ.</p>
 *
 * <p>Вкладка рецептов на этом этапе пустая: все рецепты типа {@code crafting} удалены
 * (этап 1.2), а рецепты инструментов ещё не добавлены; клиентский рендер показывает
 * пустую вкладку-заглушку (см. {@code FlatStoneScreen}).</p>
 */
public class FlatStoneMenu extends AbstractContainerMenu {
	public FlatStoneMenu(int syncId, Inventory playerInventory) {
		super(WhiteFogContent.FLAT_STONE_MENU, syncId);
	}

	/** Слотов нет — переносить нечего. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/**
	 * Пустое меню без слотов не привязано к расстоянию до блока (позиция не передаётся
	 * через обычный {@code MenuType}); это осознанно для этапа 1.4. Реальная привязка
	 * появится вместе с job-UI (этап 3.5).
	 */
	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}

package com.whitefog.mixin;

import com.whitefog.crafting.CraftingLock;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Запрет ванильного крафта на уровне обработки кликов меню.
 *
 * <p>Единая точка {@link AbstractContainerMenu#clicked(int, int, ContainerInput, Player)} покрывает
 * все пути изменения сетки крафта: обычный клик, shift-click (QUICK_MOVE), drag-вставку (QUICK_CRAFT),
 * PICKUP_ALL (двойной клик) и повторную отправку click-пакетов. Миксин общий (common), поэтому
 * отменяет клик и на клиенте тоже — это исключает предсказание и рассинхрон.</p>
 *
 * <p>Reference: {@code Zergatul/cheatutils} —
 * {@code common/java/com/zergatul/cheatutils/mixins/common/MixinAbstractContainerMenu.java}
 * (aдаптировано под Fabric 26.2).</p>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void whiteFog$blockCraftingClicks(int slotId, int button, ContainerInput input, Player player,
			CallbackInfo ci) {
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		if (CraftingLock.shouldBlockClick(menu, slotId, player)) {
			CraftingLock.onBlockedClick(menu, slotId, player);
			ci.cancel();
		}
	}
}

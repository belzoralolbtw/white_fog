package com.whitefog.mixin;

import com.whitefog.crafting.CraftingLock;

import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractCraftingMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Закрытие обходных серверных путей, не проходящих через {@code AbstractContainerMenu#clicked}.
 *
 * <ul>
 *     <li>{@code handlePlaceRecipe} — автовыкладывание рецепта из книги рецептов в сетку крафта
 *         (рецепты уже удалены, но защищаемся явно).</li>
 *     <li>{@code handleSetCreativeModeSlot} — прямая установка предмета в слот в креативе;
 *         запрещаем писать в слоты сетки/результата инвентарного меню 2x2.</li>
 * </ul>
 *
 * <p>Reference: {@code gnembon/fabric-carpet} — {@code AbstractCraftingMenu_scarpetMixin.java}
 * (перехват {@code handlePlacement}).</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
	private void whiteFog$blockRecipeBookPlacement(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
		if (this.player.containerMenu instanceof AbstractCraftingMenu) {
			ci.cancel();
		}
	}

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void whiteFog$blockCreativeCraftingSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		if (CraftingLock.isCraftingSlot(this.player.inventoryMenu, packet.slotNum())) {
			ci.cancel();
		}
	}
}

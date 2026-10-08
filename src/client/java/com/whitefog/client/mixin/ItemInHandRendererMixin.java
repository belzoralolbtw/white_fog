package com.whitefog.client.mixin;

import com.whitefog.client.portable.PortableLightClient;

import net.minecraft.client.renderer.ItemInHandRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Переносной свет для предмета в руке ОТ ПЕРВОГО ЛИЦА (тикет: first-person факел).
 *
 * <p><b>Почему этого не хватает {@code EntityRenderer}-хука (javap 26.2).</b>
 * {@code GameRenderer#renderItemInHand} подаёт в {@code ItemInHandRenderer#submitHandsWithItems}
 * ровно один {@code int packedLight}, посчитанный через
 * {@code EntityRenderDispatcher#getPackedLightCoords(player, partialTick)}. Этот путь уже освещается
 * {@code PortableLightEntityRendererMixin} ({@code EntityRenderer#getBlockLightLevel}), НО первый
 * кадр руки — отдельная submission ({@code handAndScreenSubmitNodeStorage}) со своим светом, и по
 * сообщению игрока предмет от первого лица мог оставаться невидимым. Мы явно поднимаем этот
 * аргумент до {@code max(ванильный block light, динамический от левой руки на позиции глаз)} —
 * тот же принцип, что и у reference-мода, но применённый к first-person hand-пути.</p>
 *
 * <p><b>Не ломает item renderer.</b> Light arg только повышается (никогда не понижается и не даёт
 * fullbright — не выше уровня эмиссии факела); стек/модель/display-контекст не подменяются.
 * При отсутствии валидного горящего offhand значение возвращается без изменений, поэтому обычный
 * ванильный факел и unlit/empty/corrupt видны как раньше и не светят.</p>
 *
 * <p><b>Источник-референс (прочитан, адаптирован, не скопирован):</b>
 * {@code LambdAurora/LambDynamicLights}, ветка {@code 26.2},
 * {@code src/main/java/dev/lambdaaurora/lambdynlights/mixin/EntityRendererMixin.java}
 * (хук {@code getBlockLightLevel} через {@code @ModifyReturnValue}); здесь тот же принцип {@code max}
 * для света, но на первый кадр руки, через ванильный Mixin {@code @ModifyVariable} без MixinExtras.
 * См. также {@code mixin/BrightnessGetterMixin.java} того же мода — свет меша блоков.</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

	/**
	 * Поднимает packedLight первого кадра руки. {@code index = 5} — int-параметр
	 * {@code submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)};
	 * {@code argsOnly = true} запрещает случайно зацепить локальную переменную.
	 */
	@ModifyVariable(method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "Lnet/minecraft/client/player/LocalPlayer;I)V",
			at = @At("HEAD"), argsOnly = true, index = 5)
	private int whiteFog$raiseFirstPersonLight(int light) {
		return PortableLightClient.raisePackedLight(light);
	}
}

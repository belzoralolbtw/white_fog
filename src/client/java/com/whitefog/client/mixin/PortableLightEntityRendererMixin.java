package com.whitefog.client.mixin;

import com.whitefog.client.portable.PortableLightClient;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Переносной свет от факела в левой руке для СУЩНОСТЕЙ (тикет поверх 1.9) — прежде всего предмета
 * в руке и модели игрока.
 *
 * <p><b>Почему это отдельный хук (root cause, javap 26.2).</b> В 26.2
 * {@code EntityRenderer.getPackedLightCoords(Entity, float)} собирает packed-свет не через
 * {@code LightCoordsUtil.getLightCoords}/{@code BrightnessGetter}, а напрямую:
 * {@code getBlockLightLevel(entity, pos)} + {@code getSkyLightLevel(entity, pos)} + {@code pack}.
 * {@code EntityRenderer.getBlockLightLevel} в свою очередь читает
 * {@code entity.level().getBrightness(LightLayer.BLOCK, pos)}. Поэтому хука в
 * {@code BrightnessGetter} (свет меша блоков) недостаточно: без этого миксина предмет в руке
 * освещался только ванильным block light и в вечной ночи оставался чёрным.</p>
 *
 * <p>Мы дописываем динамический вклад в block light для любой сущности в текущем клиентском мире
 * (у игрока — от его offhand, у прочих сущностей — от расстояния до той же левой руки), не выше
 * эмиссии 14/10 и не ниже ванильного значения. Только клиентский рендер, только чтение.</p>
 *
 * <p><b>Источник-референс (прочитан, адаптирован, не скопирован):</b>
 * {@code LambdAurora/LambDynamicLights}, ветка {@code 26.2},
 * {@code src/main/java/dev/lambdaurora/lambdynlights/mixin/EntityRendererMixin.java} — тот же хук
 * {@code getBlockLightLevel} с {@code @ModifyReturnValue}; заменён на ванильный Mixin
 * {@code @Inject at RETURN} без зависимости от MixinExtras.</p>
 */
@Mixin(EntityRenderer.class)
public abstract class PortableLightEntityRendererMixin {

	/**
	 * Поднимает block light до {@code max(ванильный, динамический от левой руки локального игрока)}.
	 * {@code entity.level()} — это {@code Level} (является {@code BlockGetter}); для не-текущего
	 * мира {@link PortableLightClient} вернёт 0.
	 */
	@Inject(method = "getBlockLightLevel(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)I",
			at = @At("RETURN"), cancellable = true)
	private void whiteFog$addPortableLight(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (entity == null || pos == null) {
			return;
		}
		int dynamic = PortableLightClient.dynamicBlockLight(entity.level(), pos);
		if (dynamic > cir.getReturnValue()) {
			cir.setReturnValue(dynamic);
		}
	}
}

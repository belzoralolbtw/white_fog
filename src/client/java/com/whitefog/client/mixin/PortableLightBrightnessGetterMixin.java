package com.whitefog.client.mixin;

import com.whitefog.client.portable.PortableLightClient;

import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Динамический свет от переносного факела в левой руке (этап поверх 1.6).
 *
 * <p><b>Точка инъекции (javap 26.2, minecraft-common-deobf).</b> В 26.2 ванильный
 * {@code LightCoordsUtil.getLightCoords(...)} берёт яркость у
 * {@code LightCoordsUtil.BrightnessGetter.DEFAULT} — статической лямбды
 * {@code lambda$static$0(BlockAndLightGetter, BlockPos)}. Мы дописываем в её возвращаемое
 * packed-свет значение блочной компоненты до {@code max(block, dynamic)} через
 * {@code LightCoordsUtil.withBlock}. Так динамический свет проходит через ванильное кэширование
 * яркости рендера.</p>
 *
 * <p><b>Источник-референс (прочитан, адаптирован, не скопирован):</b>
 * {@code LambdAurora/LambDynamicLights}, ветка {@code 26.2},
 * {@code src/main/java/dev/lambdaurora/lambdynlights/mixin/BrightnessGetterMixin.java} —
 * инъекция в тот же {@code lambda$static$0} того же интерфейса; и
 * {@code LambDynLights#getLightmapWithDynamicLight} — замена блочной компоненты packed-света.
 * В отличие от референса, здесь нет spatial-engine: только локальный игрок и его offhand
 * (см. {@link PortableLightClient}). Если бы инъекция не применялась, смоук/self-check сообщил бы
 * FAILURE — мы НЕ подменяем это «фейковым fullbright».</p>
 */
@Mixin(LightCoordsUtil.BrightnessGetter.class)
public interface PortableLightBrightnessGetterMixin {

	/**
	 * Дописывает переносной свет в packed-яркость. Вызывается на RETURN ванильной лямбды.
	 * {@code remap = false} — синтетическое имя лямбды не маппится.
	 */
	@Inject(method = "lambda$static$0", at = @At("RETURN"), cancellable = true, remap = false)
	private static void whiteFog$addPortableLight(BlockAndLightGetter level, BlockPos pos,
			CallbackInfoReturnable<Integer> cir) {
		int original = cir.getReturnValue();
		int dynamic = PortableLightClient.dynamicBlockLight(level, pos);
		if (dynamic <= 0) {
			return;
		}
		int blockLevel = LightCoordsUtil.block(original);
		if (dynamic <= blockLevel) {
			return;
		}
		cir.setReturnValue(LightCoordsUtil.withBlock(original, dynamic));
	}
}

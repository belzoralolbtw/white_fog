package com.whitefog.client.mixin;

import com.whitefog.client.darkness.DarknessVisualConfig;
import com.whitefog.client.darkness.DarknessVisualGate;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подавление пульсации модовой Darkness в lightmap (этап 1.5 — визуальный hotfix).
 *
 * <p><b>Что делает ваниль (javap 26.2, {@code logs\_evidence_LightmapRenderStateExtractor.txt}).</b>
 * {@code LightmapRenderStateExtractor.extract(LightmapRenderState, float)} пишет
 * {@code state.brightness = max(0, gamma - f)} и
 * {@code state.darknessEffectScale = max(0, cos((tickCount - partialTick) * PI * 0.025) * 0.45 * f) * option},
 * где {@code f = getEffectBlendFactor(DARKNESS, pt) * option}. Пульс даёт именно косинус
 * (период 80 тиков). Шейдер {@code assets/minecraft/shaders/core/lightmap.fsh}:
 * {@code color = color - vec3(DarknessScale)} затем {@code mix(color, notGamma(color), BrightnessFactor)}.</p>
 *
 * <p><b>Правка.</b> Пока {@link DarknessVisualGate#active()} (модовая тьма), мы ЗАМЕНЯЕМ
 * {@code darknessEffectScale} постоянным умеренным значением {@code MAX_DARKNESS_OFFSET * eff}
 * и поднимаем {@code brightness} до умеренного пола. Замена — не интерполяция с ванильным
 * пульсирующим значением: любая {@code lerp(...)} с текущим косинусом сохранила бы пульс.
 * {@code active()==false} => метод не трогаем, ровно ваниль (в т.ч. чужой Darkness).</p>
 *
 * <p><b>Почему TAIL.</b> {@code extract} рано возвращается при {@code !needsUpdate}, тогда
 * состояние уже содержит нашу прошлую правку (пересчёта нет) — это нормально. На обновлении
 * ({@code needsUpdate}) ваниль только что записала пульсирующее значение, и TAIL его заменяет
 * в том же кадре, поэтому чёрных импульсов нет даже на fade-in (огибающая с 0).</p>
 *
 * <p><b>API evidence (javap по minecraft-clientonly-deobf 26.2):</b>
 * {@code public void extract(net.minecraft.client.renderer.state.LightmapRenderState, float)};
 * у {@code LightmapRenderState} — public-поля {@code float brightness} и
 * {@code float darknessEffectScale}. Клиентские поля читаются на render-потоке, gate обновляется
 * там же (per-frame {@code LevelExtractionEvents.END_EXTRACTION}).</p>
 *
 * <p><b>Источник-референс (прочитан, адаптирован, не скопирован):</b> {@code Sjouwer/gamma-utils},
 * ветка {@code 26.3-Fabric}, файл
 * {@code src/main/java/io/github/sjouwer/gammautils/mixin/MixinLightmapRenderStateExtractor.java}
 * — подтверждает точку инъекции в {@code LightmapRenderStateExtractor.extract} и работу с
 * полями {@code LightmapRenderState}. Пульсирующая формула vanilla прочитана по байткоду 26.2
 * (см. evidence).</p>
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

	/**
	 * Заменяет ванильные {@code darknessEffectScale}/{@code brightness} при активной модовой тьме.
	 * {@code partialTick} не используется: наша величина постоянна и не зависит от косинуса.
	 */
	@Inject(method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
			at = @At("TAIL"))
	private void whiteFog$softenModDarkness(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		DarknessVisualGate gate = DarknessVisualGate.INSTANCE;
		if (!gate.active()) {
			// e = 0: ровно ваниль (пульсация и чужой Darkness не трогаются).
			return;
		}
		float eff = gate.strength();
		// ЗАМЕНА, а не интерполяция с пульсирующим ванильным значением.
		state.darknessEffectScale = DarknessVisualConfig.darknessOffset(eff);
		state.brightness = Math.max(state.brightness, DarknessVisualConfig.brightnessFloor(eff));
	}
}

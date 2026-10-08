package com.whitefog.client.mixin;

import com.whitefog.client.darkness.DarknessVisualConfig;
import com.whitefog.client.darkness.DarknessVisualGate;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ослабление near-black тумана модовой Darkness (этап 1.5 — визуальный hotfix).
 *
 * <p><b>Что делает ваниль (javap 26.2, {@code logs\_evidence_DarknessFogEnvironment.txt},
 * {@code _evidence_FogRenderer_full.txt}).</b>
 * {@code DarknessFogEnvironment.setupFog(FogData, Camera, ClientLevel, float, DeltaTracker)}
 * при наличии Darkness-эффекта схлопывает дистанции: {@code f = lerp(blendFactor, renderDistance, 15)},
 * {@code environmentalStart = f*0.75}, {@code environmentalEnd = skyEnd = cloudEnd = f}
 * (то есть туман ~15 блоков). {@code getModifiedDarkness(LivingEntity, float, float)} возвращает
 * {@code max(voidFactor, blendFactor)}; затем {@code FogRenderer.computeFogColor} при
 * {@code factor > 0} умножает цвет тумана на {@code square(1 - factor)} — при {@code factor=1}
 * это чёрный туман.</p>
 *
 * <p><b>Правка (только пока {@link DarknessVisualGate#active()}).</b>
 * <ol>
 *   <li>{@code setupFog}: конец тумана отодвигается до {@code MIN_FOG_DISTANCE} блоков
 *       (sky/cloud тоже), старт не дальше конца.</li>
 *   <li>{@code getModifiedDarkness}: фактор снижается до {@code MAX_FOG_DARKNESS}, но через
 *       {@code max(voidFactor, ...)} — ванильная тьма ниже мира (void) СОХРАНЯЕТСЯ, другую
 *       механику не отменяем. Точка инъекции — RETURN с {@code cancellable}: при {@code eff=0}
 *       результат совпадает с ванильным.</li>
 * </ol>
 * Применяется только к камере локального игрока ({@code entity instanceof LocalPlayer}), не к
 * remote-сущностям. {@code BLINDNESS} — отдельный {@code BlindnessFogEnvironment} и здесь не
 * затрагивается.</p>
 *
 * <p><b>API evidence (javap по minecraft-clientonly-deobf 26.2):</b>
 * {@code DarknessFogEnvironment.setupFog(FogData, Camera, ClientLevel, float, DeltaTracker)},
 * {@code DarknessFogEnvironment.getModifiedDarkness(LivingEntity, float, float)};
 * {@code FogData} — public-поля {@code environmentalStart/environmentalEnd/skyEnd/cloudEnd};
 * {@code MobEffectInstance#getBlendFactor(LivingEntity, float)}; {@code LivingEntity#getEffect(Holder)};
 * {@code MobEffects.DARKNESS}; {@code ClientPlayer} (LocalPlayer) как камера.</p>
 *
 * <p><b>Источник-референс (прочитан, адаптирован, не скопирован):</b> {@code Sjouwer/gamma-utils},
 * ветка {@code 26.3-Fabric} — работа с туманом через {@code FogRenderer.computeFogColor}
 * (подтверждает точку применения фактора darkness к цвету тумана). Точные дистанции/формулы
 * vanilla прочитаны по байткоду 26.2 (см. evidence).</p>
 */
@Mixin(DarknessFogEnvironment.class)
public abstract class DarknessFogEnvironmentMixin {

	/** Отодвигает конец тумана (environmental/sky/cloud), сохраняя старт не дальше конца. */
	@Inject(method = "setupFog(Lnet/minecraft/client/renderer/fog/FogData;Lnet/minecraft/client/Camera;"
			+ "Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/DeltaTracker;)V",
			at = @At("TAIL"))
	private void whiteFog$pushFog(FogData fog, Camera camera, ClientLevel level, float renderDistance,
			DeltaTracker deltaTracker, CallbackInfo ci) {
		DarknessVisualGate gate = DarknessVisualGate.INSTANCE;
		if (!gate.active()) {
			return;
		}
		float eff = gate.strength();
		fog.environmentalEnd = DarknessVisualConfig.fogEnd(fog.environmentalEnd, eff);
		fog.skyEnd = DarknessVisualConfig.fogEnd(fog.skyEnd, eff);
		fog.cloudEnd = DarknessVisualConfig.fogEnd(fog.cloudEnd, eff);
		if (fog.environmentalStart > fog.environmentalEnd) {
			fog.environmentalStart = fog.environmentalEnd;
		}
	}

	/**
	 * Снижает вклад модового Darkness-эффекта в затемнение цвета тумана, сохраняя void-фактор.
	 * Только камера локального игрока. Фактор ПОСТОЯННЫЙ для стабильного гейта: не зависит от
	 * ванильного {@code getBlendFactor(entity, partialTick)} (см.
	 * {@link DarknessVisualConfig#fogDarknessFactorConstant}); partialTick здесь не используется.
	 */
	@Inject(method = "getModifiedDarkness(Lnet/minecraft/world/entity/LivingEntity;FF)F",
			at = @At("RETURN"), cancellable = true)
	private void whiteFog$reduceModFogDarkness(LivingEntity entity, float voidFactor, float partialTick,
			CallbackInfoReturnable<Float> cir) {
		DarknessVisualGate gate = DarknessVisualGate.INSTANCE;
		if (!gate.active()) {
			return;
		}
		// Только локальный игрок (камера), не remote-сущности.
		if (!(entity instanceof LocalPlayer)) {
			return;
		}
		cir.setReturnValue(DarknessVisualConfig.fogDarknessFactorConstant(voidFactor, gate.strength()));
	}
}

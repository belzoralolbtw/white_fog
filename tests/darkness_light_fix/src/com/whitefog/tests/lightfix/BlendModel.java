package com.whitefog.tests.lightfix;

/**
 * Чистая модель vanilla-смешивания эффекта Darkness (26.2 {@code MobEffectInstance$BlendState}).
 *
 * <p>Источник (javap 26.2): {@code MobEffects.DARKNESS = new MobEffect(HARMFUL, ...).setBlendDuration(22)}
 * — все три компонента (in/out/advance) равны 22. {@code BlendState.tick(instance)}:
 * {@code target = !instance.endsWithin(22) ? 1 : 0}; при {@code factor != target} шаг
 * {@code clamp(target - factor, -1/dur, 1/dur)}, где {@code dur = target==1 ? blendIn : blendOut}.
 * {@code endsWithin(22) == duration <= 22}. {@code getFactor(pt)} линейно интерполирует между
 * предыдущим и текущим кадром.</p>
 *
 * <p>Это logic-only модель: она доказывает, что при обновлении эффекта ДО того, как остаток
 * дойдёт до blend-advance, фактор не «проваливается». Реальный пакет/эффект не проверяется.</p>
 */
public final class BlendModel {
	/** Blend-advance Darkness (из байткода 26.2). */
	public static final int BLEND_ADVANCE = 22;

	private float factor;
	private float factorPreviousFrame;

	/** Первое наложение эффекта: {@code setImmediate}. */
	public void setImmediate(boolean hasEffect) {
		this.factor = hasEffect ? 1.0f : 0.0f;
		this.factorPreviousFrame = this.factor;
	}

	/** Один клиентский тик. */
	public void tick(boolean hasEffect, int blendIn, int blendOut) {
		this.factorPreviousFrame = this.factor;
		float target = hasEffect ? 1.0f : 0.0f;
		if (this.factor == target) {
			return;
		}
		int duration = hasEffect ? blendIn : blendOut;
		if (duration == 0) {
			this.factor = target;
			return;
		}
		float step = 1.0f / duration;
		float diff = target - this.factor;
		if (diff > step) {
			diff = step;
		} else if (diff < -step) {
			diff = -step;
		}
		this.factor += diff;
	}

	/** Фактор на кадре (partialTick в [0,1]). */
	public float factor(float partialTick) {
		return this.factorPreviousFrame + (this.factor - this.factorPreviousFrame) * partialTick;
	}

	public float factor() {
		return this.factor;
	}

	/** {@code hasEffect}: истинно, пока остаток строго больше blend-advance. */
	public static boolean hasEffect(int remainingDuration) {
		return remainingDuration > BLEND_ADVANCE;
	}
}

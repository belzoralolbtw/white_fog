package com.whitefog.tests.portabledynamic;

/**
 * Чистая (без Minecraft) модель динамического переносного света для sandbox-теста тикета поверх 1.9.
 *
 * <p>Зеркалит <b>чистые</b> методы {@code PortableLightPolicy.attenuation/dynamicLevel/sectionRadius}
 * и обе ветки, участвующие в исправлении root cause:</p>
 * <ul>
 *   <li>{@link #oldDynamicLevel(boolean, int, double)} — прежнее поведение: при
 *       {@code level instanceof ClientLevel == false} возвращалось 0. Во время запекания меша
 *       рендер отдаёт {@code RenderSectionRegion}, поэтому реальный свет всегда был 0.</li>
 *   <li>{@link #newDynamicLevel(int, double)} — исправленное: тип getter'а больше не ограничивает
 *       вклад, учитывается только затухание.</li>
 *   <li>{@link #entityBlockLight(int, int)} — вклад в block light сущности (рука/модель игрока),
 *       которого раньше не было вовсе; без него предмет в руке оставался чёрным.</li>
 * </ul>
 *
 * <p>Это НЕ runtime-proof: реальные {@code ItemStack}/мир/рендер не участвуют.</p>
 */
public final class DynamicLightModel {
	private DynamicLightModel() {
	}

	/** Обычный факел: эмиссия 14. */
	public static final int TORCH_EMISSION = 14;
	/** Факел душ: эмиссия 10. */
	public static final int SOUL_TORCH_EMISSION = 10;

	/** Затухание: 1 уровень на блок, {@code ceil(distance)}; нечисловое/неположительное → 0. */
	public static int attenuation(double distance) {
		if (!Double.isFinite(distance) || distance <= 0.0D) {
			return 0;
		}
		return (int) Math.ceil(distance);
	}

	/** Итоговый уровень: {@code emission - attenuation}, зажат в 0..15. */
	public static int newDynamicLevel(int emission, double distance) {
		int capped = Math.max(0, Math.min(15, emission));
		int level = capped - attenuation(distance);
		return level <= 0 ? 0 : Math.min(15, level);
	}

	/**
	 * Прежнее (сломанное) поведение: если переданный getter не {@code ClientLevel}, вклад 0.
	 * Моделирует {@code if (!(level instanceof ClientLevel ...)) return 0;} из старого кода.
	 */
	public static int oldDynamicLevel(boolean serializerIsClientLevel, int emission, double distance) {
		if (!serializerIsClientLevel) {
			return 0;
		}
		return newDynamicLevel(emission, distance);
	}

	/** Вклад в block light сущности: {@code max(vanilla, dynamic)} — ровно как делает EntityRenderer-хук. */
	public static int entityBlockLight(int vanillaBlockLight, int dynamic) {
		int vanilla = Math.max(0, Math.min(15, vanillaBlockLight));
		return Math.max(vanilla, dynamic);
	}

	/**
	 * Свет first-person кадра руки: {@code max(ванильный block light, dynamicLevel(emission, distance))}
	 * — зеркало {@code PortableLightPolicy.raisedBlockLight} и хука {@code ItemInHandRenderer}.
	 */
	public static int firstPersonHandLight(int vanillaBlockLight, int emission, double distance) {
		int vanilla = Math.max(0, Math.min(15, vanillaBlockLight));
		return Math.max(vanilla, newDynamicLevel(emission, distance));
	}

	/** Радиус секций (16 блоков) вокруг источника: {@code ceil(emission / 16)}; 0 при emission 0. */
	public static int sectionRadius(int emission) {
		int capped = Math.max(0, Math.min(15, emission));
		return capped <= 0 ? 0 : (capped + 15) / 16;
	}
}

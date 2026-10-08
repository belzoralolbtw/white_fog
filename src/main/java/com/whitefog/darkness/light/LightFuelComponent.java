package com.whitefog.darkness.light;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.whitefog.WhiteFog;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Item-компонент {@code white_fog:light_fuel} хранит пару {@code (remainingTicks, lit)} (тикет поверх 1.7).
 *
 * <p>Оба поля персистентны и синхронизируются по сети. Раньше компонент хранил только
 * {@code remainingTicks} (этап 1.6); {@link LightFuel#CODEC} читает и старую int-форму, и новую
 * пару, поэтому уже выданные предметы НЕ ломаются: старый {@code remaining > 0} мигрирует в
 * {@code lit = true} (предмет продолжал гореть), {@code 0} — в {@code lit = false}.</p>
 *
 * <p>{@code lit} — «фитиль горит»; {@code remainingTicks} — остаток топлива. Источник светит и
 * расходует топливо ТОЛЬКО когда {@code lit && remainingTicks > 0} (см. {@link LightFuelPolicy}).
 * {@code Заправить} меняет только {@code remainingTicks} и НИКОГДА не включает {@code lit};
 * единственное действие {@code unlit -> lit} — {@code Зажечь}.</p>
 *
 * <p>Отсутствующий компонент = {@code (0,false)}. Заряженный ({@code remaining>0}) стек обязан
 * иметь {@code count=1} — иначе он считается повреждённым (свет не даёт, операции запрещены, split
 * не выполняется). Регистрация выполняется один раз при common-инициализации (до первого
 * использования компонента).</p>
 *
 * <p><b>API evidence (javap 26.2):</b> {@code DataComponentType.Builder#persistent(Codec)}/
 * {@code networkSynchronized(StreamCodec)}; {@code Codec.either} + {@code Either#map};
 * {@code RecordCodecBuilder.create/group/apply}; {@code StreamCodec.composite}; {@code Codec.BOOL}
 * (DFU 10.0.21).</p>
 */
public final class LightFuelComponent {

	/** Значение компонента: остаток топлива в тиках + признак «горит». */
	public record LightFuel(int remainingTicks, boolean lit) {
		/** Пустое/погасшее состояние. */
		public static final LightFuel EMPTY = new LightFuel(0, false);

		/**
		 * Новая персистентная форма: пара полей. {@code lit} необязателен и по умолчанию {@code true}
		 * (компаунд без {@code lit} — совместимость с ранними гибридными данными).
		 */
		private static final Codec<LightFuel> PAIR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("remaining").forGetter(LightFuel::remainingTicks),
				Codec.BOOL.optionalFieldOf("lit", Boolean.TRUE).forGetter(LightFuel::lit)
		).apply(instance, LightFuel::new));

		/**
		 * Персистентный кодек с обратной совместимостью: старый int-компонент
		 * ({@code Codec.INT}) читается как {@code lit = remaining > 0}; новый — как пара.
		 * Кодирование всегда идёт парой ({@code Either::right}).
		 */
		public static final Codec<LightFuel> CODEC = Codec.either(Codec.INT, PAIR_CODEC).xmap(
				either -> either.map(
						remaining -> new LightFuel(Math.max(0, remaining), remaining > 0),
						Function.identity()),
				fuel -> Either.right(fuel));

		/** Сетевой codec (VAR_INT + BOOL). */
		public static final StreamCodec<RegistryFriendlyByteBuf, LightFuel> STREAM_CODEC =
				StreamCodec.composite(
						ByteBufCodecs.VAR_INT, LightFuel::remainingTicks,
						ByteBufCodecs.BOOL, LightFuel::lit,
						LightFuel::new)
						.cast();

		/** Нормализованная копия: остаток неотрицателен. */
		public LightFuel normalized() {
			return new LightFuel(Math.max(0, remainingTicks), lit);
		}

		/** Горит ли и есть ли запас — только тогда предмет светит и расходует топливо. */
		public boolean burning() {
			return lit && remainingTicks > 0;
		}
	}

	/** Тип компонента {@code white_fog:light_fuel}; инициализация класса регистрирует его. */
	public static final DataComponentType<LightFuel> LIGHT_FUEL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, WhiteFog.id(LightConfig.FUEL_COMPONENT_PATH),
			DataComponentType.<LightFuel>builder()
					.persistent(LightFuel.CODEC)
					.networkSynchronized(LightFuel.STREAM_CODEC)
					.build());

	private LightFuelComponent() {
	}

	/** Форсирует инициализацию класса (регистрация компонента) и пишет лог. */
	public static void register() {
		WhiteFog.LOGGER.info(
				"White Fog: light fuel component registered (stage 1.7, white_fog:{} = remaining+lit)",
				LightConfig.FUEL_COMPONENT_PATH);
	}

	/** Читает компонент из стека (пустой/отсутствующий = {@link LightFuel#EMPTY}). */
	public static LightFuel read(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return LightFuel.EMPTY;
		}
		LightFuel fuel = stack.get(LIGHT_FUEL);
		return fuel == null ? LightFuel.EMPTY : fuel.normalized();
	}

	/**
	 * Читает остаток топлива из стека. Пустой стек/отсутствующий/отрицательный компонент → 0.
	 * Значение НЕ зажимается по ёмкости — это делает вызывающая сторона через
	 * {@link LightFuelPolicy#normalizeComponentValue(Integer, int)}.
	 */
	public static int readRemaining(ItemStack stack) {
		return read(stack).remainingTicks();
	}

	/** Читает признак «горит» из стека (нет компонента → {@code false}). */
	public static boolean readLit(ItemStack stack) {
		return read(stack).lit();
	}

	/** Есть ли у стека заряженный компонент (remaining &gt; 0). */
	public static boolean isCharged(ItemStack stack) {
		return readRemaining(stack) > 0;
	}

	/**
	 * Проверка «заряженный стек обязан иметь count=1»: заряженный count&gt;1 отклоняется.
	 */
	public static boolean isInvalidChargedStack(ItemStack stack, int remaining) {
		return remaining > 0 && stack.getCount() > 1;
	}

	/** Записывает пару {@code (remaining, lit)} в стек (создаёт/заменяет компонент). */
	public static void writeFuel(ItemStack stack, int remaining, boolean lit) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		stack.set(LIGHT_FUEL, new LightFuel(Math.max(0, remaining), lit));
	}
}

package com.whitefog.state;

import com.mojang.serialization.Codec;

import com.whitefog.WhiteFogConfig;
import com.whitefog.darkness.DarknessConfig;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * Серверное состояние выживания одного игрока (этап 1.1).
 *
 * <p>Класс разделён на два вида данных:</p>
 * <ul>
 *     <li><b>Игровое состояние</b> — сохраняется в NBT и синхронизируется с клиентом;</li>
 *     <li><b>Служебная информация синхронизации</b> ({@link #lastSyncedRevision}, {@link #lastSyncTick})
 *         — не сохраняется и не синхронизируется, сбрасывается при загрузке/подключении.</li>
 * </ul>
 *
 * <p>Изменения полей проходят только через сеттеры, которые зажимают значения в границы
 * {@link WhiteFogConfig} и увеличивают {@link #revision()}. По изменению ревизии сервер понимает,
 * что пора отправить клиенту свежий снимок состояния.</p>
 *
 * <p>Сериализация выполняется вручную в {@link CompoundTag}: это даёт полный контроль над
 * форматом и делает код независимым от количества полей (в отличие от {@code RecordCodecBuilder},
 * который ограничен 16 компонентами).</p>
 */
public final class PlayerSurvivalState {
	/**
	 * Codec для persistence-вложения Fabric Data Attachment API:
	 * NBT-компаунд ({@link CompoundTag#CODEC}) отображается в объект состояния и обратно.
	 */
	public static final Codec<PlayerSurvivalState> CODEC =
			CompoundTag.CODEC.xmap(PlayerSurvivalState::fromNbt, PlayerSurvivalState::toNbt);

	// --- Ключи NBT ---
	private static final String TAG_WEIGHT_KG = "weight_kg";
	private static final String TAG_FATIGUE = "fatigue";
	private static final String TAG_BODY = "body";
	private static final String TAG_TEMPERATURE_C = "temperature_c";
	private static final String TAG_CALORIES = "calories";
	private static final String TAG_WATER = "water";
	private static final String TAG_CONDITION = "condition";
	private static final String TAG_CLOTHING_WETNESS = "clothing_wetness";
	private static final String TAG_DYSENTERY_REMAINING = "dysentery_remaining";
	private static final String TAG_DYSENTERY_LAST_PROCESSED = "dysentery_last_processed";
	private static final String TAG_SLEEP_ACTIVE = "sleep_active";
	private static final String TAG_SLEEP_STARTED = "sleep_started";
	private static final String TAG_SLEEP_LAST_PROCESSED = "sleep_last_processed";
	private static final String TAG_WORK_ACTIVE = "work_active";
	private static final String TAG_WORK_STARTED = "work_started";
	private static final String TAG_WORK_LAST_PROCESSED = "work_last_processed";
	private static final String TAG_ACTIVE_GOAL = "active_goal";
	private static final String TAG_GOAL_STARTED = "goal_started";
	private static final String TAG_REVISION = "revision";

	// --- Ключи NBT этапа 1.5 (воздействие тьмы) ---
	private static final String TAG_DARKNESS_SCHEMA = "darkness_schema";
	private static final String TAG_LIGHT_EXPOSURE = "light_exposure";
	private static final String TAG_SAFE_LIGHT_TICKS = "safe_light_ticks";
	private static final String TAG_SAMPLE_REMAINDER = "sample_remainder_ticks";
	private static final String TAG_DARKNESS_CONDITION_MILLI = "darkness_condition_milli";
	private static final String TAG_DARKNESS_REVISION = "darkness_revision";

	// ------------------------------------------------------------------
	// Игровое состояние (сохраняется и синхронизируется)
	// ------------------------------------------------------------------

	/** Вес в килограммах, точность 0.1 кг. */
	private double weightKg = WhiteFogConfig.DEFAULT_WEIGHT_KG;
	/** Усталость 0–100. */
	private float fatigue = WhiteFogConfig.DEFAULT_FATIGUE;
	/** Состояние тела 0–100. */
	private float body = WhiteFogConfig.DEFAULT_BODY;
	/** Ощущаемая температура в градусах Цельсия. */
	private float perceivedTemperatureC = WhiteFogConfig.DEFAULT_TEMPERATURE_C;
	/** Калории 0–3000. */
	private float calories = WhiteFogConfig.DEFAULT_CALORIES;
	/** Вода 0–100. */
	private float water = WhiteFogConfig.DEFAULT_WATER;
	/** Condition 0–100. */
	private float condition = WhiteFogConfig.DEFAULT_CONDITION;
	/** Мокрота по слотам одежды, индекс — порядковый номер {@link ClothingSlot}. */
	private final float[] clothingWetness = new float[ClothingSlot.values().length];
	/** Оставшееся время дизентерии в тиках (0 — болезнь отсутствует). */
	private int dysenteryRemainingTicks = WhiteFogConfig.DEFAULT_DYSENTERY_TICKS;
	/** Последний обработанный тик дизентерии (для обработки только реально прошедшего интервала). */
	private long dysenteryLastProcessedAtTick = 0L;
	/** Активен ли сон. */
	private boolean sleepActive = false;
	/** Тик начала текущего сна. */
	private long sleepStartedAtTick = 0L;
	/** Последний обработанный тик сна. */
	private long sleepLastProcessedAtTick = 0L;
	/** Активна ли работа. */
	private boolean workActive = false;
	/** Тик начала текущей работы. */
	private long workStartedAtTick = 0L;
	/** Последний обработанный тик работы. */
	private long workLastProcessedAtTick = 0L;
	/** Идентификатор активной цели (пустая строка — цели нет). */
	private String activeGoal = "";
	/** Тик, на котором была установлена активная цель. */
	private long goalStartedAtTick = 0L;
	/** Ревизия состояния: увеличивается при каждом изменении для детекта синхронизации. */
	private long revision = 0L;

	// ------------------------------------------------------------------
	// Этап 1.5 — воздействие тьмы (сохраняется, синхронизируется отдельным снимком)
	// ------------------------------------------------------------------

	/** Версия схемы полей тьмы (для будущих миграций). */
	private int darknessSchema = DarknessConfig.DARKNESS_SCHEMA_VERSION;
	/** Воздействие тьмы 0..100 (0 — вне тьмы, 100 — полная тьма). */
	private int lightExposure = DarknessConfig.EXPOSURE_MIN;
	/** Счётчик непрерывного светлого отдыха 0..600. */
	private int safeLightTicks = DarknessConfig.SAFE_TICKS_MIN;
	/** Остаток накопления до следующего sample 0..19 (одна секунда = 20 тиков). */
	private int sampleRemainderTicks = DarknessConfig.SAMPLE_REMAINDER_MIN;
	/** Condition в тысячных п.п. 0..100000; существующий {@link #condition} отображает это значение. */
	private int darknessConditionMilli = DarknessConfig.CONDITION_MILLI_MAX;
	/** Ревизия полей тьмы: увеличивается при изменении для детекта синхронизации снимка тьмы. */
	private long darknessRevision = 0L;

	// ------------------------------------------------------------------
	// Служебные поля синхронизации (НЕ сохраняются и НЕ синхронизируются)
	// ------------------------------------------------------------------

	/** Ревизия, которая уже была отправлена клиенту (-1 — ещё не отправляли). */
	private long lastSyncedRevision = -1L;
	/** Тик последней отправки состояния клиенту. */
	private long lastSyncTick = Long.MIN_VALUE;

	/** Создаёт состояние со всеми безопасными значениями по умолчанию. */
	public PlayerSurvivalState() {
		for (int i = 0; i < this.clothingWetness.length; i++) {
			this.clothingWetness[i] = WhiteFogConfig.DEFAULT_CLOTHING_WETNESS;
		}
	}

	/** Явная фабрика значений по умолчанию (используется инициализатором вложения). */
	public static PlayerSurvivalState createDefault() {
		return new PlayerSurvivalState();
	}

	// ------------------------------------------------------------------
	// Сериализация
	// ------------------------------------------------------------------

	/** Читает состояние из NBT. Отсутствующие ключи заменяются безопасными значениями. */
	public static PlayerSurvivalState fromNbt(CompoundTag tag) {
		PlayerSurvivalState state = new PlayerSurvivalState();
		state.weightKg = clampWeight(tag.getDoubleOr(TAG_WEIGHT_KG, WhiteFogConfig.DEFAULT_WEIGHT_KG));
		state.fatigue = clampF(tag.getFloatOr(TAG_FATIGUE, WhiteFogConfig.DEFAULT_FATIGUE),
				WhiteFogConfig.FATIGUE_MIN, WhiteFogConfig.FATIGUE_MAX);
		state.body = clampF(tag.getFloatOr(TAG_BODY, WhiteFogConfig.DEFAULT_BODY),
				WhiteFogConfig.BODY_MIN, WhiteFogConfig.BODY_MAX);
		state.perceivedTemperatureC = clampF(tag.getFloatOr(TAG_TEMPERATURE_C, WhiteFogConfig.DEFAULT_TEMPERATURE_C),
				WhiteFogConfig.TEMPERATURE_MIN_C, WhiteFogConfig.TEMPERATURE_MAX_C);
		state.calories = clampF(tag.getFloatOr(TAG_CALORIES, WhiteFogConfig.DEFAULT_CALORIES),
				WhiteFogConfig.CALORIES_MIN, WhiteFogConfig.CALORIES_MAX);
		state.water = clampF(tag.getFloatOr(TAG_WATER, WhiteFogConfig.DEFAULT_WATER),
				WhiteFogConfig.WATER_MIN, WhiteFogConfig.WATER_MAX);
		state.condition = clampF(tag.getFloatOr(TAG_CONDITION, WhiteFogConfig.DEFAULT_CONDITION),
				WhiteFogConfig.CONDITION_MIN, WhiteFogConfig.CONDITION_MAX);

		CompoundTag wetness = tag.getCompoundOrEmpty(TAG_CLOTHING_WETNESS);
		for (ClothingSlot slot : ClothingSlot.values()) {
			state.clothingWetness[slot.ordinal()] = clampF(
					wetness.getFloatOr(slot.serializedName(), WhiteFogConfig.DEFAULT_CLOTHING_WETNESS),
					WhiteFogConfig.WETNESS_MIN, WhiteFogConfig.WETNESS_MAX);
		}

		state.dysenteryRemainingTicks = Math.max(WhiteFogConfig.DYSENTERY_MIN_TICKS,
				Math.min(WhiteFogConfig.DYSENTERY_MAX_TICKS,
						tag.getIntOr(TAG_DYSENTERY_REMAINING, WhiteFogConfig.DEFAULT_DYSENTERY_TICKS)));
		state.dysenteryLastProcessedAtTick = Math.max(0L, tag.getLongOr(TAG_DYSENTERY_LAST_PROCESSED, 0L));

		state.sleepActive = tag.getBooleanOr(TAG_SLEEP_ACTIVE, false);
		state.sleepStartedAtTick = Math.max(0L, tag.getLongOr(TAG_SLEEP_STARTED, 0L));
		state.sleepLastProcessedAtTick = Math.max(0L, tag.getLongOr(TAG_SLEEP_LAST_PROCESSED, 0L));

		state.workActive = tag.getBooleanOr(TAG_WORK_ACTIVE, false);
		state.workStartedAtTick = Math.max(0L, tag.getLongOr(TAG_WORK_STARTED, 0L));
		state.workLastProcessedAtTick = Math.max(0L, tag.getLongOr(TAG_WORK_LAST_PROCESSED, 0L));

		state.activeGoal = tag.getStringOr(TAG_ACTIVE_GOAL, "");
		state.goalStartedAtTick = Math.max(0L, tag.getLongOr(TAG_GOAL_STARTED, 0L));

		state.revision = Math.max(0L, tag.getLongOr(TAG_REVISION, 0L));

		// --- Этап 1.5: миграция и чтение полей тьмы ---
		state.darknessSchema = Math.max(1, tag.getIntOr(TAG_DARKNESS_SCHEMA, DarknessConfig.DARKNESS_SCHEMA_VERSION));
		state.lightExposure = clampI(tag.getIntOr(TAG_LIGHT_EXPOSURE, DarknessConfig.EXPOSURE_MIN),
				DarknessConfig.EXPOSURE_MIN, DarknessConfig.EXPOSURE_MAX);
		state.safeLightTicks = clampI(tag.getIntOr(TAG_SAFE_LIGHT_TICKS, DarknessConfig.SAFE_TICKS_MIN),
				DarknessConfig.SAFE_TICKS_MIN, DarknessConfig.SAFE_TICKS_MAX);
		state.sampleRemainderTicks = clampI(tag.getIntOr(TAG_SAMPLE_REMAINDER, DarknessConfig.SAMPLE_REMAINDER_MIN),
				DarknessConfig.SAMPLE_REMAINDER_MIN, DarknessConfig.SAMPLE_REMAINDER_MAX);
		// Миграция: при отсутствии поля Condition берётся из существующего condition (п.п. -> тысячные п.п.).
		int migratedConditionMilli = Math.round(clampF(state.condition,
				WhiteFogConfig.CONDITION_MIN, WhiteFogConfig.CONDITION_MAX) * 1000.0F);
		state.darknessConditionMilli = clampI(
				tag.getIntOr(TAG_DARKNESS_CONDITION_MILLI, migratedConditionMilli),
				DarknessConfig.CONDITION_MILLI_MIN, DarknessConfig.CONDITION_MILLI_MAX);
		// Существующий Condition отображает результат: милли — источник истины.
		state.condition = state.darknessConditionMilli / 1000.0F;
		state.darknessRevision = Math.max(0L, tag.getLongOr(TAG_DARKNESS_REVISION, 0L));
		// Служебные поля синхронизации всегда начинают с чистого листа.
		state.invalidateSync();
		state.normalize();
		return state;
	}

	/** Записывает состояние в NBT. */
	public CompoundTag toNbt() {
		CompoundTag tag = new CompoundTag();
		tag.putDouble(TAG_WEIGHT_KG, this.weightKg);
		tag.putFloat(TAG_FATIGUE, this.fatigue);
		tag.putFloat(TAG_BODY, this.body);
		tag.putFloat(TAG_TEMPERATURE_C, this.perceivedTemperatureC);
		tag.putFloat(TAG_CALORIES, this.calories);
		tag.putFloat(TAG_WATER, this.water);
		tag.putFloat(TAG_CONDITION, this.condition);

		CompoundTag wetness = new CompoundTag();
		for (ClothingSlot slot : ClothingSlot.values()) {
			wetness.putFloat(slot.serializedName(), this.clothingWetness[slot.ordinal()]);
		}
		tag.put(TAG_CLOTHING_WETNESS, wetness);

		tag.putInt(TAG_DYSENTERY_REMAINING, this.dysenteryRemainingTicks);
		tag.putLong(TAG_DYSENTERY_LAST_PROCESSED, this.dysenteryLastProcessedAtTick);

		tag.putBoolean(TAG_SLEEP_ACTIVE, this.sleepActive);
		tag.putLong(TAG_SLEEP_STARTED, this.sleepStartedAtTick);
		tag.putLong(TAG_SLEEP_LAST_PROCESSED, this.sleepLastProcessedAtTick);

		tag.putBoolean(TAG_WORK_ACTIVE, this.workActive);
		tag.putLong(TAG_WORK_STARTED, this.workStartedAtTick);
		tag.putLong(TAG_WORK_LAST_PROCESSED, this.workLastProcessedAtTick);

		tag.putString(TAG_ACTIVE_GOAL, this.activeGoal);
		tag.putLong(TAG_GOAL_STARTED, this.goalStartedAtTick);

		tag.putLong(TAG_REVISION, this.revision);

		// --- Этап 1.5: поля тьмы ---
		tag.putInt(TAG_DARKNESS_SCHEMA, this.darknessSchema);
		tag.putInt(TAG_LIGHT_EXPOSURE, this.lightExposure);
		tag.putInt(TAG_SAFE_LIGHT_TICKS, this.safeLightTicks);
		tag.putInt(TAG_SAMPLE_REMAINDER, this.sampleRemainderTicks);
		tag.putInt(TAG_DARKNESS_CONDITION_MILLI, this.darknessConditionMilli);
		tag.putLong(TAG_DARKNESS_REVISION, this.darknessRevision);
		return tag;
	}

	// ------------------------------------------------------------------
	// Нормализация и утилиты
	// ------------------------------------------------------------------

	/** Зажимает все значения в допустимые границы и округляет вес. Не трогает ревизию. */
	public void normalize() {
		this.weightKg = clampWeight(this.weightKg);
		this.fatigue = clampF(this.fatigue, WhiteFogConfig.FATIGUE_MIN, WhiteFogConfig.FATIGUE_MAX);
		this.body = clampF(this.body, WhiteFogConfig.BODY_MIN, WhiteFogConfig.BODY_MAX);
		this.perceivedTemperatureC = clampF(this.perceivedTemperatureC,
				WhiteFogConfig.TEMPERATURE_MIN_C, WhiteFogConfig.TEMPERATURE_MAX_C);
		this.calories = clampF(this.calories, WhiteFogConfig.CALORIES_MIN, WhiteFogConfig.CALORIES_MAX);
		this.water = clampF(this.water, WhiteFogConfig.WATER_MIN, WhiteFogConfig.WATER_MAX);
		this.condition = clampF(this.condition, WhiteFogConfig.CONDITION_MIN, WhiteFogConfig.CONDITION_MAX);
		for (int i = 0; i < this.clothingWetness.length; i++) {
			this.clothingWetness[i] = clampF(this.clothingWetness[i],
					WhiteFogConfig.WETNESS_MIN, WhiteFogConfig.WETNESS_MAX);
		}
		this.dysenteryRemainingTicks = Math.max(WhiteFogConfig.DYSENTERY_MIN_TICKS,
				Math.min(WhiteFogConfig.DYSENTERY_MAX_TICKS, this.dysenteryRemainingTicks));
		this.dysenteryLastProcessedAtTick = Math.max(0L, this.dysenteryLastProcessedAtTick);
		this.sleepStartedAtTick = Math.max(0L, this.sleepStartedAtTick);
		this.sleepLastProcessedAtTick = Math.max(0L, this.sleepLastProcessedAtTick);
		this.workStartedAtTick = Math.max(0L, this.workStartedAtTick);
		this.workLastProcessedAtTick = Math.max(0L, this.workLastProcessedAtTick);
		this.goalStartedAtTick = Math.max(0L, this.goalStartedAtTick);
		if (this.activeGoal == null) {
			this.activeGoal = "";
		}
		if (this.revision < 0L) {
			this.revision = 0L;
		}

		// --- Этап 1.5: нормализация полей тьмы ---
		if (this.darknessSchema < 1) {
			this.darknessSchema = DarknessConfig.DARKNESS_SCHEMA_VERSION;
		}
		this.lightExposure = clampI(this.lightExposure, DarknessConfig.EXPOSURE_MIN, DarknessConfig.EXPOSURE_MAX);
		this.safeLightTicks = clampI(this.safeLightTicks, DarknessConfig.SAFE_TICKS_MIN, DarknessConfig.SAFE_TICKS_MAX);
		this.sampleRemainderTicks = clampI(this.sampleRemainderTicks,
				DarknessConfig.SAMPLE_REMAINDER_MIN, DarknessConfig.SAMPLE_REMAINDER_MAX);
		this.darknessConditionMilli = clampI(this.darknessConditionMilli,
				DarknessConfig.CONDITION_MILLI_MIN, DarknessConfig.CONDITION_MILLI_MAX);
		// Condition всегда отображает милли-значение (единый источник истины).
		this.condition = this.darknessConditionMilli / 1000.0F;
		if (this.darknessRevision < 0L) {
			this.darknessRevision = 0L;
		}
	}

	private static float clampF(float value, float min, float max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/** Зажимает целое в границы. */
	private static int clampI(int value, int min, int max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/** Зажимает вес в границы и округляет до шага 0.1 кг. */
	private static double clampWeight(double value) {
		double clamped = value;
		if (clamped < WhiteFogConfig.WEIGHT_MIN_KG) {
			clamped = WhiteFogConfig.WEIGHT_MIN_KG;
		}
		if (clamped > WhiteFogConfig.WEIGHT_MAX_KG) {
			clamped = WhiteFogConfig.WEIGHT_MAX_KG;
		}
		return Math.round(clamped / WhiteFogConfig.WEIGHT_STEP_KG) * WhiteFogConfig.WEIGHT_STEP_KG;
	}

	private void bumpRevision() {
		this.revision++;
	}

	/** Увеличивает ревизию полей тьмы (для детекта синхронизации снимка тьмы). */
	private void bumpDarknessRevision() {
		this.darknessRevision++;
	}

	// ------------------------------------------------------------------
	// Ревизия и синхронизация
	// ------------------------------------------------------------------

	public long revision() {
		return this.revision;
	}

	public long lastSyncedRevision() {
		return this.lastSyncedRevision;
	}

	public long lastSyncTick() {
		return this.lastSyncTick;
	}

	/** Помечает текущую ревизию как отправленную на указанном тике. */
	public void markSynced(long tick) {
		this.lastSyncedRevision = this.revision;
		this.lastSyncTick = tick;
	}

	/** Заставляет отправить состояние клиенту при следующей возможности. */
	public void invalidateSync() {
		this.lastSyncedRevision = -1L;
		this.lastSyncTick = Long.MIN_VALUE;
	}

	/** Есть ли несинхронизированные изменения. */
	public boolean hasUnsyncedChanges() {
		return this.revision != this.lastSyncedRevision;
	}

	// ------------------------------------------------------------------
	// Геттеры/сеттеры игрового состояния
	// ------------------------------------------------------------------

	public double getWeightKg() {
		return this.weightKg;
	}

	public void setWeightKg(double value) {
		this.weightKg = clampWeight(value);
		bumpRevision();
	}

	public float getFatigue() {
		return this.fatigue;
	}

	public void setFatigue(float value) {
		this.fatigue = clampF(value, WhiteFogConfig.FATIGUE_MIN, WhiteFogConfig.FATIGUE_MAX);
		bumpRevision();
	}

	public float getBody() {
		return this.body;
	}

	public void setBody(float value) {
		this.body = clampF(value, WhiteFogConfig.BODY_MIN, WhiteFogConfig.BODY_MAX);
		bumpRevision();
	}

	public float getPerceivedTemperatureC() {
		return this.perceivedTemperatureC;
	}

	public void setPerceivedTemperatureC(float value) {
		this.perceivedTemperatureC = clampF(value,
				WhiteFogConfig.TEMPERATURE_MIN_C, WhiteFogConfig.TEMPERATURE_MAX_C);
		bumpRevision();
	}

	public float getCalories() {
		return this.calories;
	}

	public void setCalories(float value) {
		this.calories = clampF(value, WhiteFogConfig.CALORIES_MIN, WhiteFogConfig.CALORIES_MAX);
		bumpRevision();
	}

	public float getWater() {
		return this.water;
	}

	public void setWater(float value) {
		this.water = clampF(value, WhiteFogConfig.WATER_MIN, WhiteFogConfig.WATER_MAX);
		bumpRevision();
	}

	public float getCondition() {
		return this.condition;
	}

	public void setCondition(float value) {
		this.condition = clampF(value, WhiteFogConfig.CONDITION_MIN, WhiteFogConfig.CONDITION_MAX);
		// Синхронизируем точное значение тыс. п.п., чтобы Condition не расходился с механикой тьмы.
		this.darknessConditionMilli = clampI(Math.round(this.condition * 1000.0F),
				DarknessConfig.CONDITION_MILLI_MIN, DarknessConfig.CONDITION_MILLI_MAX);
		bumpRevision();
		bumpDarknessRevision();
	}

	/** Мокрота указанного слота одежды, 0–100. */
	public float getClothingWetness(ClothingSlot slot) {
		return this.clothingWetness[slot.ordinal()];
	}

	/** Задаёт мокроту указанного слота одежды, 0–100. */
	public void setClothingWetness(ClothingSlot slot, float value) {
		this.clothingWetness[slot.ordinal()] = clampF(value, WhiteFogConfig.WETNESS_MIN, WhiteFogConfig.WETNESS_MAX);
		bumpRevision();
	}

	public int getDysenteryRemainingTicks() {
		return this.dysenteryRemainingTicks;
	}

	/** Задаёт оставшееся время дизентерии в тиках (0 — болезнь отсутствует). */
	public void setDysenteryRemainingTicks(int ticks) {
		this.dysenteryRemainingTicks = Math.max(WhiteFogConfig.DYSENTERY_MIN_TICKS,
				Math.min(WhiteFogConfig.DYSENTERY_MAX_TICKS, ticks));
		bumpRevision();
	}

	public long getDysenteryLastProcessedAtTick() {
		return this.dysenteryLastProcessedAtTick;
	}

	public void setDysenteryLastProcessedAtTick(long tick) {
		this.dysenteryLastProcessedAtTick = Math.max(0L, tick);
		bumpRevision();
	}

	/** Запускает болезнь: {@code ticks} — длительность, {@code nowTick} — текущий игровой тик. */
	public void beginDysentery(int ticks, long nowTick) {
		this.dysenteryRemainingTicks = Math.max(WhiteFogConfig.DYSENTERY_MIN_TICKS,
				Math.min(WhiteFogConfig.DYSENTERY_MAX_TICKS, ticks));
		this.dysenteryLastProcessedAtTick = Math.max(0L, nowTick);
		bumpRevision();
	}

	/** Полностью вылечивает болезнь. */
	public void clearDysentery() {
		this.dysenteryRemainingTicks = WhiteFogConfig.DYSENTERY_MIN_TICKS;
		this.dysenteryLastProcessedAtTick = 0L;
		bumpRevision();
	}

	public boolean isSleepActive() {
		return this.sleepActive;
	}

	public long getSleepStartedAtTick() {
		return this.sleepStartedAtTick;
	}

	public long getSleepLastProcessedAtTick() {
		return this.sleepLastProcessedAtTick;
	}

	/** Начинает сон: фиксирует {@code startedAt} и {@code lastProcessedAt}. */
	public void beginSleep(long nowTick) {
		this.sleepActive = true;
		this.sleepStartedAtTick = Math.max(0L, nowTick);
		this.sleepLastProcessedAtTick = Math.max(0L, nowTick);
		bumpRevision();
	}

	/** Завершает сон (значение {@code startedAt} сохраняется как история последнего сна). */
	public void endSleep() {
		this.sleepActive = false;
		bumpRevision();
	}

	public void setSleepLastProcessedAtTick(long tick) {
		this.sleepLastProcessedAtTick = Math.max(0L, tick);
		bumpRevision();
	}

	public boolean isWorkActive() {
		return this.workActive;
	}

	public long getWorkStartedAtTick() {
		return this.workStartedAtTick;
	}

	public long getWorkLastProcessedAtTick() {
		return this.workLastProcessedAtTick;
	}

	/** Начинает работу: фиксирует {@code startedAt} и {@code lastProcessedAt}. */
	public void beginWork(long nowTick) {
		this.workActive = true;
		this.workStartedAtTick = Math.max(0L, nowTick);
		this.workLastProcessedAtTick = Math.max(0L, nowTick);
		bumpRevision();
	}

	/** Завершает работу. */
	public void endWork() {
		this.workActive = false;
		bumpRevision();
	}

	public void setWorkLastProcessedAtTick(long tick) {
		this.workLastProcessedAtTick = Math.max(0L, tick);
		bumpRevision();
	}

	public String getActiveGoal() {
		return this.activeGoal;
	}

	public long getGoalStartedAtTick() {
		return this.goalStartedAtTick;
	}

	/** Устанавливает активную цель; пустая строка означает «цели нет». */
	public void setActiveGoal(String goal, long nowTick) {
		this.activeGoal = goal == null ? "" : goal;
		this.goalStartedAtTick = Math.max(0L, nowTick);
		bumpRevision();
	}

	/** Сбрасывает активную цель. */
	public void clearActiveGoal() {
		this.activeGoal = "";
		this.goalStartedAtTick = 0L;
		bumpRevision();
	}

	// ------------------------------------------------------------------
	// Этап 1.5 — геттеры/сеттеры полей тьмы
	// ------------------------------------------------------------------

	/** Версия схемы полей тьмы. */
	public int getDarknessSchema() {
		return this.darknessSchema;
	}

	/** Воздействие тьмы 0..100. */
	public int getLightExposure() {
		return this.lightExposure;
	}

	/** Задаёт exposure; ревизия тьмы растёт только при фактическом изменении. */
	public void setLightExposure(int value) {
		int clamped = clampI(value, DarknessConfig.EXPOSURE_MIN, DarknessConfig.EXPOSURE_MAX);
		if (clamped == this.lightExposure) {
			return;
		}
		this.lightExposure = clamped;
		bumpDarknessRevision();
	}

	/** Счётчик непрерывного светлого отдыха 0..600. */
	public int getSafeLightTicks() {
		return this.safeLightTicks;
	}

	/** Задаёт счётчик отдыха; ревизия тьмы растёт только при фактическом изменении. */
	public void setSafeLightTicks(int value) {
		int clamped = clampI(value, DarknessConfig.SAFE_TICKS_MIN, DarknessConfig.SAFE_TICKS_MAX);
		if (clamped == this.safeLightTicks) {
			return;
		}
		this.safeLightTicks = clamped;
		bumpDarknessRevision();
	}

	/** Остаток накопления до следующего sample 0..19 (служебное, ревизию не меняет). */
	public int getSampleRemainderTicks() {
		return this.sampleRemainderTicks;
	}

	/** Задаёт остаток накопления; не увеличивает ни одну ревизию (иначе был бы лишний снимок каждый тик). */
	public void setSampleRemainderTicks(int value) {
		this.sampleRemainderTicks = clampI(value,
				DarknessConfig.SAMPLE_REMAINDER_MIN, DarknessConfig.SAMPLE_REMAINDER_MAX);
	}

	/** Condition в тысячных п.п. 0..100000. */
	public int getDarknessConditionMilli() {
		return this.darknessConditionMilli;
	}

	/**
	 * Задаёт Condition в тысячных п.п. Обновляет и {@link #getCondition()} (для базового HUD),
	 * и ревизию тьмы. Ревизии растут только при фактическом изменении.
	 */
	public void setDarknessConditionMilli(int value) {
		int clamped = clampI(value, DarknessConfig.CONDITION_MILLI_MIN, DarknessConfig.CONDITION_MILLI_MAX);
		if (clamped == this.darknessConditionMilli) {
			return;
		}
		this.darknessConditionMilli = clamped;
		this.condition = clamped / 1000.0F;
		bumpRevision();
		bumpDarknessRevision();
	}

	/** Ревизия полей тьмы. */
	public long getDarknessRevision() {
		return this.darknessRevision;
	}

	// ------------------------------------------------------------------
	// Отладочное представление (без изменения данных)
	// ------------------------------------------------------------------

	/** Возвращает человекочитаемое описание состояния. Данные не изменяются. */
	public String describe(long nowTick) {
		StringBuilder sb = new StringBuilder();
		sb.append("weight=").append(String.format(Locale.ROOT, "%.1f", this.weightKg)).append("kg");
		sb.append(" fatigue=").append(fmt(this.fatigue));
		sb.append(" body=").append(fmt(this.body));
		sb.append(" temp=").append(fmt(this.perceivedTemperatureC)).append("C");
		sb.append(" calories=").append(fmt(this.calories));
		sb.append(" water=").append(fmt(this.water));
		sb.append(" condition=").append(fmt(this.condition));
		sb.append(" wetness=[");
		for (ClothingSlot slot : ClothingSlot.values()) {
			if (slot.ordinal() > 0) {
				sb.append(", ");
			}
			sb.append(slot.serializedName()).append('=').append(fmt(this.clothingWetness[slot.ordinal()]));
		}
		sb.append(']');
		sb.append(" dysentery=").append(this.dysenteryRemainingTicks).append("t");
		if (this.dysenteryRemainingTicks > 0) {
			sb.append("(lastProcessed=").append(this.dysenteryLastProcessedAtTick).append(')');
		}
		sb.append(" sleep=").append(this.sleepActive
				? ("on startedAt=" + this.sleepStartedAtTick + " lastProcessed=" + this.sleepLastProcessedAtTick
						+ " duration=" + (nowTick - this.sleepStartedAtTick) + "t")
				: "off");
		sb.append(" work=").append(this.workActive
				? ("on startedAt=" + this.workStartedAtTick + " lastProcessed=" + this.workLastProcessedAtTick
						+ " duration=" + (nowTick - this.workStartedAtTick) + "t")
				: "off");
		sb.append(" goal=").append(this.activeGoal.isEmpty()
				? "none"
				: ("'" + this.activeGoal + "' startedAt=" + this.goalStartedAtTick));
		sb.append(" darkness=[schema=").append(this.darknessSchema)
				.append(" exposure=").append(this.lightExposure)
				.append(" safeTicks=").append(this.safeLightTicks)
				.append(" remainder=").append(this.sampleRemainderTicks)
				.append(" conditionMilli=").append(this.darknessConditionMilli)
				.append(" darknessRevision=").append(this.darknessRevision)
				.append(']');
		sb.append(" revision=").append(this.revision);
		return sb.toString();
	}

	private static String fmt(float value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}

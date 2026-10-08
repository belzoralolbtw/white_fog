package com.whitefog.client.portable;

import com.whitefog.WhiteFog;
import com.whitefog.darkness.light.PortableLightPolicy;
import com.whitefog.darkness.light.PortableLightService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * Клиентский расчёт динамического (переносного) света от факела/факела душ в ЛЕВОЙ руке локального
 * игрока. Используется mixin'ами в {@code LightCoordsUtil.BrightnessGetter} (свет меша блочных
 * моделей) и в {@code EntityRenderer.getBlockLightLevel} (свет сущностей — в т.ч. предмета в руке).
 *
 * <p><b>Исправление (root cause, ticket поверх 1.9).</b> Прежняя версия требовала
 * {@code level instanceof ClientLevel}. Но {@code LightCoordsUtil.BrightnessGetter.lambda$static$0}
 * во время запекания меша секции получает не {@code ClientLevel}, а
 * {@code net.minecraft.client.renderer.chunk.RenderSectionRegion} (javap 26.2: реализует
 * {@code BlockAndTintGetter} → {@code BlockAndLightGetter}, но НЕ {@code ClientLevel}). Поэтому
 * блок-свет всегда получал 0. Теперь тип принимается как {@link BlockGetter}, а измерение
 * сверяется только когда это действительно {@link ClientLevel}. Источник света —
 * {@code Minecraft#level}/{@code player} (текущий клиентский мир), блок-состояние берётся у
 * переданного getter'а.</p>
 *
 * <p><b>Dynamic light в руке.</b> {@code EntityRenderer.getPackedLightCoords} в 26.2 НЕ вызывает
 * {@code LightCoordsUtil.getLightCoords} — он берёт {@code entity.level().getBrightness(BLOCK, pos)}
 * напрямую. Поэтому одного хука на {@code BrightnessGetter} мало: предмет в руке оставался
 * чёрным. Второй миксин ({@code PortableLightEntityRendererMixin}) поднимает block light на
 * {@code getBlockLightLevel}, чем освещает руку/модель игрока.</p>
 *
 * <p>Источник-референс (прочитан, адаптирован, не скопирован): {@code LambdAurora/LambDynamicLights},
 * ветка {@code 26.2} —
 * {@code mixin/BrightnessGetterMixin.java} (хук в {@code lambda$static$0}) и
 * {@code mixin/EntityRendererMixin.java} ({@code @ModifyReturnValue} на
 * {@code EntityRenderer.getBlockLightLevel}). Полноценный spatial-engine/планировщик
 * перестройки секций у нас заменён минимальным трекером смены offhand-света
 * ({@link #tickSectionRebuilds(Minecraft)}).</p>
 */
public final class PortableLightClient {

	/** Последняя эмиссия offhand (0..15) для отслеживания изменения света. */
	private static int lastEmission = 0;
	/** Последняя позиция игрока (секция/блок), откуда исходил переносной свет. */
	private static BlockPos lastSourcePos = null;

	private PortableLightClient() {
	}

	/**
	 * Динамический block light (0..15) от offhand локального игрока в позиции {@code pos}.
	 *
	 * @param level getter, через который рендер спрашивает блок: в мире это {@code ClientLevel}, при
	 *              запекании меша — {@code RenderSectionRegion}, в GUI — {@code BlockAndTintGetter.EMPTY}.
	 */
	public static int dynamicBlockLight(BlockGetter level, BlockPos pos) {
		if (level == null || pos == null) {
			return 0;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return 0;
		}
		LocalPlayer player = client.player;
		// Только текущий клиентский мир: без уровня/игрока света нет.
		if (player == null || client.level == null) {
			return 0;
		}
		// Светят только две реальные точки рендера: сущность/мир (ClientLevel) и запекание меша
		// секции (RenderSectionRegion). Прочие getter'ы (например, BlockAndTintGetter.EMPTY в GUI)
		// не светим, чтобы предметы в интерфейсе не «подсвечивались» от игрока.
		boolean entityOrWorld = level instanceof ClientLevel;
		boolean meshBaking = level instanceof RenderSectionRegion;
		if (!entityOrWorld && !meshBaking) {
			return 0;
		}
		// Если рендер отдал настоящий ClientLevel — не светим в чужом измерении.
		if (level instanceof ClientLevel clientLevel
				&& !clientLevel.dimension().equals(player.level().dimension())) {
			return 0;
		}
		ItemStack offhand = player.getOffhandItem();
		int emission = PortableLightService.emissionForStack(offhand);
		if (emission <= 0) {
			return 0;
		}
		// Непрозрачный блок сам по себе не освещаем (как LambDynamicLights).
		if (level.getBlockState(pos).isSolidRender()) {
			return 0;
		}
		double distance = Math.sqrt(player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)));
		return PortableLightPolicy.dynamicLevel(emission, distance);
	}

	/** Последний залогированный ключ диагностики first-person света (-1 = ещё не логировали). */
	private static int lastLoggedHandLight = -1;

	/**
	 * Повышает block-компоненту packed-света для FIRST-PERSON руки/предмета до
	 * {@code max(ванильный block light, динамический от левой руки)}. Нужен потому, что первый
	 * кадр руки ({@code GameRenderer#renderItemInHand}) подаёт в {@code ItemInHandRenderer} ровно
	 * один packed-свет, и этот путь должен сам учитывать переносной источник. Хук идемпотентен:
	 * если {@code EntityRenderer#getBlockLightLevel} уже поднял свет, значение не меняется.
	 *
	 * <p>Без валидного горящего offhand ({@code lit}+{@code remaining>0}+{@code count==1}) вход
	 * возвращается байт-в-байт — unlit/empty/corrupt не светят, ванильный факел виден как обычно.</p>
	 */
	public static int raisePackedLight(int packedLight) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null || client.level == null) {
			return packedLight;
		}
		LocalPlayer player = client.player;
		int emission = PortableLightService.emissionForStack(player.getOffhandItem());
		if (emission <= 0) {
			return packedLight;
		}
		BlockPos pos = BlockPos.containing(player.getEyePosition());
		int dynamic = dynamicBlockLight(player.level(), pos);
		int blockLevel = LightCoordsUtil.block(packedLight);
		logHandLight(emission, blockLevel, dynamic);
		if (dynamic <= blockLevel) {
			return packedLight;
		}
		return LightCoordsUtil.withBlock(packedLight, dynamic);
	}

	/**
	 * Диагностика (одна строка на изменение состояния) — runtime-доказательство того, что
	 * first-person путь руки реально исполняется и какой свет он получает. Отсутствие строки при
	 * валидном offhand означает, что предмет в руку не подавался. В headless smoke игрока нет.
	 */
	private static void logHandLight(int emission, int blockLevel, int dynamic) {
		int key = (emission << 16) | ((blockLevel & 0xFF) << 8) | (dynamic & 0xFF);
		if (key == lastLoggedHandLight) {
			return;
		}
		lastLoggedHandLight = key;
		WhiteFog.LOGGER.info(
				"WHITEFOG_FP_HAND_LIGHT offhandEmission={} packedBlockBefore={} dynamic={}",
				emission, blockLevel, dynamic);
	}

	/**
	 * Минимальная замена spatial-engine LambDynamicLights: пока игрок держит горящий факел в левой
	 * руке, свет «запекается» в меш секции. В 26.2 меш не пересобирается сам при перемещении
	 * источника, не входящего в light-engine (javap: {@code BlockModelLighter.Cache} кэширует
	 * яркость на время сборки секции). Поэтому при смене эмиссии offhand или позиции игрока мы
	 * помечаем секции вокруг старого и нового источника dirty через
	 * {@code Minecraft.levelExtractor.setSectionDirty(...)} — при следующей сборке они вновь
	 * спросят {@link #dynamicBlockLight}. Вызывается из клиентского тика.
	 */
	public static void tickSectionRebuilds(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			lastEmission = 0;
			lastSourcePos = null;
			return;
		}
		int emission = PortableLightService.emissionForStack(client.player.getOffhandItem());
		BlockPos pos = client.player.blockPosition();
		boolean sameEmission = emission == lastEmission;
		boolean samePos = lastSourcePos != null && lastSourcePos.equals(pos);
		if (sameEmission && samePos) {
			return;
		}
		// Убираем свет со старых секций (если он был) и зажигаем на новых.
		if (lastEmission > 0 && lastSourcePos != null) {
			schedule(client, lastSourcePos, lastEmission);
		}
		if (emission > 0) {
			schedule(client, pos, emission);
		}
		lastEmission = emission;
		lastSourcePos = pos.immutable();
	}

	/** Помечает секции в радиусе действия света dirty (клиентский рендер-экстрактор). */
	private static void schedule(Minecraft client, BlockPos origin, int emission) {
		if (client.levelExtractor == null) {
			return;
		}
		int radius = PortableLightPolicy.sectionRadius(emission);
		if (radius <= 0) {
			return;
		}
		int centerX = origin.getX() >> 4;
		int centerY = origin.getY() >> 4;
		int centerZ = origin.getZ() >> 4;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					client.levelExtractor.setSectionDirty(centerX + dx, centerY + dy, centerZ + dz);
				}
			}
		}
	}
}

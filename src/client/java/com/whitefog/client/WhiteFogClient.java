package com.whitefog.client;

import com.whitefog.WhiteFog;
import com.whitefog.client.darkness.DarknessVisualGate;
import com.whitefog.client.dev.ItemModelSelfCheck;
import com.whitefog.client.hud.LightWorkPanelHud;
import com.whitefog.client.hud.WhiteFogHud;
import com.whitefog.client.network.DarknessClientNetworking;
import com.whitefog.client.network.LightClientNetworking;
import com.whitefog.client.network.WhiteFogClientNetworking;
import com.whitefog.client.portable.PortableLightClient;
import com.whitefog.client.screen.FlatStoneScreen;
import com.whitefog.client.screen.LightSourceScreen;
import com.whitefog.darkness.light.PortableLightService;
import com.whitefog.network.LightRefuelPayload;
import com.whitefog.network.LightSourceSnapshotPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.loader.api.FabricLoader;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.effect.MobEffects;

import java.lang.reflect.Method;

/**
 * Клиентский инициализатор мода «Белая Мгла».
 *
 * <p>Делает только клиентское: регистрирует получатель синхронизации и заглушку HUD,
 * а при отключении очищает клиентский кэш. Никакой серверной логики здесь нет.</p>
 *
 * <p>В dev-среде дополнительно выполняется <b>bounded self-check</b> клиентского миксина
 * {@code MultiPlayerGameModeMixin} (hotfix этапа 1.3): класс
 * {@link MultiPlayerGameMode} в ваниле создаётся только при входе в мир, поэтому headless
 * smoke до главного меню его не загружает. Мы принудительно загружаем целевой класс (это и
 * запускает применение миксина) и по reflection проверяем, что инъецированные обработчики
 * попали в класс. Строка {@code MultiPlayerGameModeMixin applied=...} пишется в лог и служит
 * доказательством для {@code scripts/client_smoke.*}.</p>
 */
@Environment(EnvType.CLIENT)
public class WhiteFogClient implements ClientModInitializer {
	/** Клавиша Work Panel (этап 1.6): {@code G}, через {@code InputConstants.KEY_G}. */
	private static final KeyMapping LIGHT_PANEL_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.white_fog.work_panel", InputConstants.KEY_G, KeyMapping.Category.MISC));

	/** Монотонный sequence запросов refuel (дедупликация на сервере). */
	private static int lightRequestSequence = 0;

	/** Клиентский кэш светового состояния (доступен экрану источника). */
	private static ClientLightState lightStateRef;

	/** Доступ к клиентскому световому состоянию (для экрана источника). */
	public static ClientLightState lightState() {
		return lightStateRef;
	}

	/** Доступ к клавише Work Panel (для HUD). */
	public static KeyMapping lightPanelKey() {
		return LIGHT_PANEL_KEY;
	}

	@Override
	public void onInitializeClient() {
		ClientPlayerState playerState = new ClientPlayerState();
		ClientDarknessState darknessState = new ClientDarknessState();
		ClientLightState lightState = new ClientLightState();
		lightStateRef = lightState;

		// Получатель S2C (тип payload уже зарегистрирован в common initializer).
		WhiteFogClientNetworking.register(playerState);

		// Этап 1.5: получатель снимков тьмы (тип зарегистрирован в common до ресивера).
		DarknessClientNetworking.register(darknessState);

		// Этап 1.6: получатели снимка источника и результата refuel.
		LightClientNetworking.register(lightState);

		// Заглушка HUD (MC 26.2 Fabric HudElementRegistry).
		WhiteFogHud.register(playerState);

		// Этап 1.6: Work Panel источников света (клавиша G).
		LightWorkPanelHud.register(lightState);

		// Этап 1.5 (визуальный hotfix): per-frame обновление огибающей модовой тьмы.
		// Fabric END_EXTRACTION даёт DeltaTracker (frame-delta) и fires раз в кадр в мире;
		// gate хранит bounded-огибающую, которую читают LightmapRenderStateExtractorMixin и
		// DarknessFogEnvironmentMixin. Только local player/camera, не remote-сущности.
		LevelExtractionEvents.END_EXTRACTION.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.player == null) {
				DarknessVisualGate.INSTANCE.reset();
				return;
			}
			LocalPlayer player = client.player;
			double dt = context.deltaTracker().getRealtimeDeltaTicks() / 20.0;
			double userScale = client.options.darknessEffectScale().get();
			DarknessVisualGate.INSTANCE.update(dt, darknessState,
					player.isAlive(), player.isCreative(), player.isSpectator(),
					player.hasEffect(MobEffects.DARKNESS), userScale);
		});

		// Этап 1.4: экран пустой станции «Плоский камень» (регистрация MenuScreens).
		FlatStoneScreen.register();
		WhiteFog.LOGGER.info("White Fog: flat-stone screen registered (stage 1.4)");

		// Этап поверх 1.6: экран источника света (ПКМ на torch/lantern/campfire).
		LightSourceScreen.register();
		WhiteFog.LOGGER.info("White Fog: light source screen registered (stage 1.7)");

		// Этап 1.5: зеркальный клиентский запрет спринта, пока сервер держит speedRestricted.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			handleLightPanelKey(lightState);
			if (client.player == null) {
				return;
			}
			if (darknessState.speedRestricted() && client.player.isSprinting()) {
				client.player.setSprinting(false);
			}
			// Тикет поверх 1.9: динамический переносной свет в меш секций. В 26.2 меш не
			// пересобирается сам от источника, не входящего в light-engine, поэтому при смене
			// offhand-света/позиции помечаем секции dirty (см. PortableLightClient).
			PortableLightClient.tickSectionRebuilds(client);
			// Диагностика (одна строка на изменение) — доказательство состояния offhand-света в
			// реальной игре; в headless smoke игрока нет и строка не пишется.
			logPortableLightState(client);
		});
		WhiteFog.LOGGER.info("White Fog: darkness snapshot receiver registered (stage 1.5)");
		WhiteFog.LOGGER.info("White Fog: light work panel registered (stage 1.6, keybind G)");

		// Сбрасываем кэши при выходе из мира.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			playerState.clear();
			darknessState.clear();
			lightState.clear();
			// Визуальный адаптер тьмы: мгновенный сброс огибающей/хвоста (без залипшего затемнения).
			DarknessVisualGate.INSTANCE.reset();
		});

		// Dev-маркер client init + проверка применения клиентского миксина (для bounded client smoke).
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			boolean mixinApplied = verifyBlockBreakClientMixin();
			WhiteFog.LOGGER.info(
					"White Fog: client initializer ready (stage 1.3 regression fix), MultiPlayerGameModeMixin applied={}",
					mixinApplied);

			// Этап 1.5 (визуальный hotfix): dev-only проверка применения миксинов тьмы.
			// Принудительно загружает целевые классы (это и запускает применение миксинов) и
			// проверяет наличие обработчиков. Доказательство для bounded visual probe; НЕ пиксели.
			boolean darknessVisualApplied = verifyDarknessVisualMixins();
			WhiteFog.LOGGER.info("WHITEFOG_DARKNESS_VISUAL_SELFTEST handlers={} status={}",
					darknessVisualApplied, darknessVisualApplied ? "SUCCESS" : "FAILURE");

			// Этап поверх 1.6: dev-only проверка применения mixin динамического переносного света.
			boolean portableLightApplied = verifyPortableLightMixin();
			WhiteFog.LOGGER.info("WHITEFOG_PORTABLE_LIGHT_SELFTEST handlers={} status={}",
					portableLightApplied, portableLightApplied ? "SUCCESS" : "FAILURE");

			// Dev-only: проверка реального bake item-моделей контента (см. ItemModelSelfCheck).
			ItemModelSelfCheck.register();
		}
	}

	/** Нажатие G: отправляет C2S {@code white_fog:light_refuel} по последнему снимку источника. */
	private static void handleLightPanelKey(ClientLightState lightState) {
		if (!LIGHT_PANEL_KEY.consumeClick()) {
			return;
		}
		LightSourceSnapshotPayload snapshot = lightState.snapshot();
		if (snapshot == null || !snapshot.present() || snapshot.pos() == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(LightRefuelPayload.TYPE)) {
			return;
		}
		lightRequestSequence++;
		ClientPlayNetworking.send(new LightRefuelPayload(snapshot.dimension(), snapshot.pos(),
				snapshot.sourceUuid(), snapshot.revision(), lightRequestSequence));
	}

	/**
	 * Ожидаемые обработчики клиентского миксина (regression fix этапа 1.3):
	 * подавление ванильного предсказания на start/continue и ручной ABORT при отпускании ЛКМ.
	 */
	private static final String[] EXPECTED_BLOCK_BREAK_HANDLERS = {
		"whiteFog$onStartDestroyBlock",
		"whiteFog$onContinueDestroyBlock",
		"whiteFog$onStopDestroyBlock"
	};

	/**
	 * Принудительно загружает {@link MultiPlayerGameMode} (только в dev) и проверяет, что
	 * инъецированные миксином обработчики присутствуют в классе.
	 *
	 * <p>Имена обработчиков {@code @Inject} Mixin может префиксовать ({@code handler$...$...}),
	 * поэтому проверяем вхождение уникальных суффиксов, а не полное имя. При сбое применения
	 * миксина загрузка класса выбросит {@code MixinApplyError}/{@code NoClassDefFoundError} —
	 * исключение ловится и логируется, чтобы smoke получил точную причину. Считаем миксин
	 * применённым, только если найдены ВСЕ три обработчика.</p>
	 */
	private static boolean verifyBlockBreakClientMixin() {
		try {
			Class<?> target = MultiPlayerGameMode.class;
			boolean[] found = new boolean[EXPECTED_BLOCK_BREAK_HANDLERS.length];
			for (Method method : target.getDeclaredMethods()) {
				String name = method.getName();
				for (int i = 0; i < EXPECTED_BLOCK_BREAK_HANDLERS.length; i++) {
					if (name.contains(EXPECTED_BLOCK_BREAK_HANDLERS[i])) {
						found[i] = true;
					}
				}
			}
			for (boolean present : found) {
				if (!present) {
					return false;
				}
			}
			return true;
		} catch (Throwable t) {
			WhiteFog.LOGGER.error("White Fog: client mixin self-check failed (MultiPlayerGameModeMixin)", t);
			return false;
		}
	}

	/** Ожидаемые обработчики клиентских миксинов визуального адаптера тьмы (этап 1.5). */
	private static final String[] EXPECTED_LIGHTMAP_HANDLERS = { "whiteFog$softenModDarkness" };
	private static final String[] EXPECTED_DARKNESS_FOG_HANDLERS = {
		"whiteFog$pushFog",
		"whiteFog$reduceModFogDarkness"
	};

	/**
	 * Принудительно загружает {@link LightmapRenderStateExtractor} и {@link DarknessFogEnvironment}
	 * (только в dev) и проверяет, что инъецированные миксинами обработчики присутствуют в классах.
	 * Загрузка класса запускает применение миксина; при сбое будет исключение, которое ловится и
	 * логируется. Возвращает {@code true}, только если найдены ВСЕ обработчики.
	 */
	private static boolean verifyDarknessVisualMixins() {
		try {
			boolean lightmap = hasHandlers(LightmapRenderStateExtractor.class, EXPECTED_LIGHTMAP_HANDLERS);
			boolean fog = hasHandlers(DarknessFogEnvironment.class, EXPECTED_DARKNESS_FOG_HANDLERS);
			return lightmap && fog;
		} catch (Throwable t) {
			WhiteFog.LOGGER.error("White Fog: darkness visual mixin self-check failed", t);
			return false;
		}
	}

	/** Ожидаемый обработчик клиентских миксинов динамического переносного света (этап поверх 1.6). */
	private static final String[] EXPECTED_PORTABLE_LIGHT_HANDLERS = { "whiteFog$addPortableLight" };
	/** Ожидаемый обработчик first-person кадра руки (тикет: свет предмета в руке от 1-го лица). */
	private static final String[] EXPECTED_FIRST_PERSON_HAND_HANDLERS = { "whiteFog$raiseFirstPersonLight" };

	/** Последняя залогированная эмиссия offhand (диагностика, -1 = ещё не логировали). */
	private static int lastLoggedPortableEmission = -1;

	/**
	 * Принудительно загружает {@link LightCoordsUtil.BrightnessGetter} (свет меша блоков) и
	 * {@link EntityRenderer} (свет сущностей/предмета в руке), проверяя, что оба миксина
	 * динамического света применились. Загрузка класса запускает применение миксина; при сбое
	 * будет исключение, которое ловится и логируется.
	 */
	private static boolean verifyPortableLightMixin() {
		try {
			boolean mesh = hasHandlers(LightCoordsUtil.BrightnessGetter.class, EXPECTED_PORTABLE_LIGHT_HANDLERS);
			boolean entity = hasHandlers(EntityRenderer.class, EXPECTED_PORTABLE_LIGHT_HANDLERS);
			// Тикет first-person: отдельный световой аргумент кадра руки (ItemInHandRenderer).
			boolean hand = hasHandlers(ItemInHandRenderer.class, EXPECTED_FIRST_PERSON_HAND_HANDLERS);
			return mesh && entity && hand;
		} catch (Throwable t) {
			WhiteFog.LOGGER.error("White Fog: portable light mixin self-check failed", t);
			return false;
		}
	}

	/**
	 * Пишет одну строку {@code WHITEFOG_PORTABLE_LIGHT_STATE ...} при изменении эмиссии offhand —
	 * runtime-доказательство (не sandbox): какой предмет в левой руке, сколько даёт света. В
	 * headless smoke игрока нет, поэтому строка там отсутствует.
	 */
	private static void logPortableLightState(Minecraft client) {
		int emission = PortableLightService.emissionForStack(client.player.getOffhandItem());
		if (emission == lastLoggedPortableEmission) {
			return;
		}
		lastLoggedPortableEmission = emission;
		WhiteFog.LOGGER.info(
				"WHITEFOG_PORTABLE_LIGHT_STATE offhand={} emission={} dynamicAtEye={}",
				client.player.getOffhandItem().getItem(),
				emission,
				PortableLightClient.dynamicBlockLight(client.level, client.player.blockPosition()));
	}

	/** Есть ли в объявленных методах класса все ожидаемые обработчики (по вхождению суффикса). */
	private static boolean hasHandlers(Class<?> target, String[] expected) {
		for (String suffix : expected) {
			boolean found = false;
			for (Method method : target.getDeclaredMethods()) {
				if (method.getName().contains(suffix)) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}
}

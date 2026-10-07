package com.whitefog.client;

import com.whitefog.WhiteFog;
import com.whitefog.client.dev.ItemModelSelfCheck;
import com.whitefog.client.hud.WhiteFogHud;
import com.whitefog.client.network.WhiteFogClientNetworking;
import com.whitefog.client.screen.FlatStoneScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;

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
	@Override
	public void onInitializeClient() {
		ClientPlayerState playerState = new ClientPlayerState();

		// Получатель S2C (тип payload уже зарегистрирован в common initializer).
		WhiteFogClientNetworking.register(playerState);

		// Заглушка HUD (MC 26.2 Fabric HudElementRegistry).
		WhiteFogHud.register(playerState);

		// Этап 1.4: экран пустой станции «Плоский камень» (регистрация MenuScreens).
		FlatStoneScreen.register();
		WhiteFog.LOGGER.info("White Fog: flat-stone screen registered (stage 1.4)");

		// Сбрасываем кэш при выходе из мира.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> playerState.clear());

		// Dev-маркер client init + проверка применения клиентского миксина (для bounded client smoke).
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			boolean mixinApplied = verifyBlockBreakClientMixin();
			WhiteFog.LOGGER.info(
					"White Fog: client initializer ready (stage 1.3 regression fix), MultiPlayerGameModeMixin applied={}",
					mixinApplied);

			// Dev-only: проверка реального bake item-моделей контента (см. ItemModelSelfCheck).
			ItemModelSelfCheck.register();
		}
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
}

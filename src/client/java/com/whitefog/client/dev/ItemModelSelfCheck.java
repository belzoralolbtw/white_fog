package com.whitefog.client.dev;

import com.whitefog.WhiteFog;
import com.whitefog.content.WhiteFogContent;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;

/**
 * Dev-only self-check этапа 1.4: проверяет, что item-модели контента мода
 * ({@code white_fog:flat_stone}, {@code white_fog:small_stone}) реально запечены
 * клиентом и не подменены {@code MissingItemModel}. Нужен потому, что {@code /give}
 * сообщает успех даже при отсутствующей/невидимой предметной модели, а headless-лог
 * этого не показывает.
 *
 * <p>Пишет ровно одну строку
 * {@code WHITEFOG_ITEM_MODEL_SELFTEST flat[key=... inRegistry=... model=... quads=...]
 * small[...] status=SUCCESS|FAIL|NOT_READY}. Проверка повторяется ограниченное число
 * тиков: до завершения первичного resource reload {@code ModelManager#getItemModel}
 * бросает NPE (baked-карт ещё нет).</p>
 *
 * <p>API проверен javap по 26.2 clientonly deobf jar: {@code Minecraft#getModelManager()},
 * {@code ModelManager#getItemModel(Identifier)}; {@code quads} — приватное поле
 * {@code CuboidItemModelWrapper}/{@code MissingItemModel} типа {@code QuadCollection}
 * (read-only reflection, ломает проверку только при смене имени поля).</p>
 */
@Environment(EnvType.CLIENT)
public final class ItemModelSelfCheck {

	/** Максимум тиков ожидания готовности клиента (20 т/с → ~10 c). */
	private static final int MAX_ATTEMPTS = 200;

	private static boolean done = false;
	private static int attempts = 0;

	private ItemModelSelfCheck() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ItemModelSelfCheck::onTick);
	}

	private static void onTick(Minecraft client) {
		if (done || client == null) {
			return;
		}
		attempts++;
		try {
			if (client.getModelManager() == null) {
				returnUnlessGaveUp();
				return;
			}

			Identifier flatKey = BuiltInRegistries.ITEM.getKey(WhiteFogContent.FLAT_STONE_ITEM);
			Identifier smallKey = BuiltInRegistries.ITEM.getKey(WhiteFogContent.SMALL_STONE_ITEM);
			// 1.9: те же данные источника света используют ВАНИЛЬНУЮ модель факела; компонент
			// white_fog:light_fuel не должен её подменять/скрывать. Проверяем, что bake не даёт
			// MissingItemModel и quads != 0 для torch/soul_torch (наравне с прочими).
			Identifier torchKey = Identifier.withDefaultNamespace("torch");
			Identifier soulTorchKey = Identifier.withDefaultNamespace("soul_torch");
			ItemModel flatModel = client.getModelManager().getItemModel(flatKey);
			ItemModel smallModel = client.getModelManager().getItemModel(smallKey);
			ItemModel torchModel = client.getModelManager().getItemModel(torchKey);
			ItemModel soulTorchModel = client.getModelManager().getItemModel(soulTorchKey);
			if (flatModel == null || smallModel == null || torchModel == null || soulTorchModel == null) {
				returnUnlessGaveUp();
				return;
			}

			String flat = describe(flatModel, flatKey);
			String small = describe(smallModel, smallKey);
			String torch = describe(torchModel, torchKey);
			String soulTorch = describe(soulTorchModel, soulTorchKey);
			boolean ok = isHealthy(flat) && isHealthy(small) && isHealthy(torch) && isHealthy(soulTorch);
			done = true;
			WhiteFog.LOGGER.info("WHITEFOG_ITEM_MODEL_SELFTEST flat[{}] small[{}] torch[{}] soulTorch[{}] status={}",
					flat, small, torch, soulTorch, ok ? "SUCCESS" : "FAIL");
		} catch (Throwable t) {
			// Транзиентное состояние загрузки клиента: модель ещё не запечена
			// (NPE в ModelManager#getItemModel). Ждём следующий тик; о неудаче сообщаем
			// только после MAX_ATTEMPTS.
			returnUnlessGaveUp(t);
		}
	}

	/** Модель запечена и не пустая (нет MissingItemModel, quads присутствуют). */
	private static boolean isHealthy(String described) {
		return !described.contains("model=MissingItemModel")
				&& !described.contains("quads=0")
				&& !described.contains("quads=-1");
	}

	private static String describe(ItemModel model, Identifier key) {
		return "key=" + key
				+ " inRegistry=" + BuiltInRegistries.ITEM.containsKey(key)
				+ " model=" + model.getClass().getSimpleName()
				+ " quads=" + countQuads(model);
	}

	private static int countQuads(ItemModel model) {
		try {
			Field field = model.getClass().getDeclaredField("quads");
			field.setAccessible(true);
			Object value = field.get(model);
			if (value instanceof QuadCollection collection) {
				return collection.getAll().size();
			}
		} catch (Throwable ignored) {
			// Не cuboid-модель — quads здесь не посчитать.
		}
		return -1;
	}

	private static void returnUnlessGaveUp() {
		returnUnlessGaveUp(null);
	}

	private static void returnUnlessGaveUp(Throwable transientError) {
		if (attempts >= MAX_ATTEMPTS) {
			done = true;
			WhiteFog.LOGGER.warn("WHITEFOG_ITEM_MODEL_SELFTEST status=NOT_READY attempts={} lastError={}",
					attempts, transientError == null ? "n/a" : transientError.toString());
		}
	}
}

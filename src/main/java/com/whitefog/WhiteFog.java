package com.whitefog;

import com.whitefog.breaking.BreakTimerService;
import com.whitefog.content.WhiteFogContent;
import com.whitefog.crafting.CraftingLock;
import com.whitefog.network.WhiteFogPayloads;
import com.whitefog.server.WhiteFogServer;
import com.whitefog.station.FlatStoneInteractions;
import com.whitefog.station.RecoveryService;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Общий (common) инициализатор мода «Белая Мгла».
 *
 * <p>Порядок инициализации важен:</p>
 * <ol>
 *     <li>{@link WhiteFogPayloads#register()} — регистрируем типы custom payload РОВНО ОДИН РАЗ
 *         до каких-либо ресиверов (клиентский ресивер регистрируется позже, в client initializer);</li>
 *     <li>{@link WhiteFogAttachments#register()} — регистрируем персистентное серверное состояние игрока;</li>
 *     <li>{@link WhiteFogServer#register()} — ровно один серверный тик-обработчик и (в dev-среде) отладочная команда.</li>
 *     <li>{@link CraftingLock#register()} — запрет ванильного крафта (inventory 2x2 + верстак 3x3).</li>
 *     <li>{@link BreakTimerService#register()} — правила разрушения блоков/станций (этап 1.3).</li>
 * </ol>
 *
 * <p>Common-код не импортирует {@code net.minecraft.client.*} — клиентские классы живут только
 * в client source set.</p>
 */
public class WhiteFog implements ModInitializer {
	public static final String MOD_ID = "white_fog";

	/** Логгер мода (пишет в logs/latest.log и в console). */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 1) Типы пакетов — до регистрации получателей.
		WhiteFogPayloads.register();

		// 2) Персистентное серверное состояние игрока.
		WhiteFogAttachments.register();

		// 3) Серверный тик (один обработчик) и dev-команда.
		WhiteFogServer.register();

		// 4) Запрет ванильного крафта (этап 1.2): инвентарная сетка 2x2 и верстак 3x3.
		CraftingLock.register();

		// 5) Правила разрушения блоков и станций (этап 1.3): сервис сессий и таймера.
		BreakTimerService.register();

		// 6) Контент этапа 1.4: блоки/BlockItem flat_stone + small_stone, block entity, меню.
		WhiteFogContent.register();

		// 7) Серверные interaction'ы станции/камушка и recovery-задача (этап 1.4).
		FlatStoneInteractions.register();
		RecoveryService.register();

		LOGGER.info("White Fog common initialized (mod id: {})", MOD_ID);
	}

	/** Создаёт {@link Identifier} в пространстве имён мода. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

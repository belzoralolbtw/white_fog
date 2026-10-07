package com.whitefog.breaking;

import com.whitefog.WhiteFog;
import com.whitefog.WhiteFogConfig;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Серверно-авторитетный сервис разрушения блоков и снятия станций (этап 1.3).
 *
 * <p>Перехват выполняется в {@code ServerPlayerGameMode#handleBlockBreakAction}
 * (см. {@code com.whitefog.mixin.ServerPlayerGameModeMixin}) для трёх действий
 * 26.2: {@code START_DESTROY_BLOCK} / {@code STOP_DESTROY_BLOCK} / {@code ABORT_DESTROY_BLOCK}.
 * Ванильный прогресс и ванильный дроп для контролируемых сессий подавляются, а завершение
 * определяет ТОЛЬКО серверный тик ({@link #tickAll(MinecraftServer)}), а не клиентский progress bar.</p>
 *
 * <p>Поскольку миксин отменяет ванильный {@code handleBlockBreakAction} на {@code HEAD} для
 * покрытых блоков, все ванильные гейты доступа (дистанция, границы мира, spawn protection,
 * {@code mayInteract}, {@code blockActionRestricted}, неразрушимость) продублированы здесь
 * на сервере — при START, каждом tick/STOP и повторно при commit.</p>
 *
 * <p>Состояние — сервисные карты по UUID игрока (не static-поля игрового состояния внутри
 * сущностей); очищаются при disconnect/смене измерения/respawn.</p>
 *
 * <p><b>Особенность 26.2 (block-state prediction, javap-проверено по
 * {@code net.minecraft.client.multiplayer.MultiPlayerGameMode}):</b> клиент при завершении
 * своего (ванильного) прогресса оптимистично применяет изменение блока и шлёт {@code STOP_DESTROY_BLOCK},
 * а сервер в {@code ServerGamePacketListenerImpl#handlePlayerAction} затем безусловно вызывает
 * {@code ackBlockChangesUpTo(seq)}. Поэтому преждевременный {@code STOP} НЕ отменяет сессию
 * (иначе медленное разрушение стало бы невозможным) — отмена происходит по {@code ABORT},
 * а также по смерти/disconnect/смене измерения/блока/инструмента/дистанции/наполненности.</p>
 *
 * <p><b>Источники-референсы (адаптировано, не скопировано):</b></p>
 * <ul>
 *     <li>{@code Patbox/polymer} ({@code dev/26.2}) —
 *         {@code polymer-core/.../mixin/block/ServerPlayerGameModeMixin.java}: серверный mining,
 *         {@code destroyAndAck}, пакеты разрушения/обновления блока.</li>
 *     <li>{@code FabricMC/fabric-api} — {@code fabric-events-interaction-v0/.../ServerPlayerGameModeMixin.java}:
 *         точка входа {@code handleBlockBreakAction} и проверка {@code isWithinBlockInteractionRange(pos, 1.0)}.</li>
 *     <li>{@code gnembon/fabric-carpet} — {@code ServerGamePacketListenerImpl_interactionUpdatesMixin.java}:
 *         порядок {@code handlePlayerAction} → {@code handleBlockBreakAction}.</li>
 *     <li>{@code Tschipp/CarryOn} ({@code 26.2}) — {@code Common/.../carry/PickupHandler.java}:
 *         безопасное снятие станции (removeBlockEntity + removeBlock, без дублей).</li>
 * </ul>
 */
public final class BreakTimerService {
	/** Сообщение отказа (утверждено ROADMAP_STEPS.md, этап 1.3). */
	public static final Component MESSAGE_TOO_HARD = Component.literal("Слишком крепко — нужен инструмент");

	/** Причина отказа (для cooldown подсказки на игрока/причину). */
	private enum RefuseReason {
		NO_TOOL,
		HOT,
		FILLED
	}

	/** Причина отмены активной сессии (для dev-диагностики). */
	private enum AbortReason {
		OK,
		DEAD,
		NO_PERMISSION,
		OUT_OF_BOUNDS,
		CHUNK_UNLOADED,
		DIMENSION_CHANGED,
		TOOL_CHANGED,
		BLOCK_CHANGED,
		UNBREAKABLE,
		OUT_OF_RANGE,
		HOT,
		FILLED
	}

	/** Активные сессии по UUID игрока. Изменяются только в серверном потоке. */
	private static final Map<UUID, BreakSession> SESSIONS = new HashMap<>();

	/** Последний тик подсказки отказа, на игрока и причину. */
	private static final Map<UUID, Map<RefuseReason, Long>> LAST_MESSAGE = new HashMap<>();

	/** Защита от повторной регистрации (не игровое состояние). */
	private static boolean registered = false;

	private BreakTimerService() {
	}

	/** Регистрирует сервис (идемпотентно). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WhiteFog.LOGGER.info("White Fog: block-break rules registered (stage 1.3, server-authoritative timer)");
	}

	// ------------------------------------------------------------------
	// Точка входа из миксина
	// ------------------------------------------------------------------

	/**
	 * Обрабатывает действие игрока по блоку.
	 *
	 * @return {@code true} — ванильный {@code handleBlockBreakAction} нужно отменить
	 *         (действие обработано модом либо безопасный отказ);
	 *         {@code false} — блок не покрыт правилами, пусть работает ваниль (со своими гейтами).
	 */
	public static boolean handleAction(ServerPlayer player, ServerLevel level, BlockPos pos,
			ServerboundPlayerActionPacket.Action action, int sequence) {
		try {
			// Ванильный гейт «слишком высоко» (handleBlockBreakAction: pos.y > level.getMaxY()).
			if (pos.getY() > level.getMaxY()) {
				resyncState(player, level, pos);
				return true;
			}
			// Дистанция проверяется ДО чтения BlockState — ванильный гейт «too far».
			if (!player.isWithinBlockInteractionRange(pos, WhiteFogConfig.BREAK_INTERACTION_RANGE_MARGIN)) {
				return true;
			}
			return switch (action) {
				case START_DESTROY_BLOCK -> onStart(player, level, pos);
				case STOP_DESTROY_BLOCK -> onStop(player, level, pos);
				case ABORT_DESTROY_BLOCK -> onAbort(player, level, pos);
				default -> false;
			};
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: block-break action failed (player={}, pos={}, action={})",
					player.getStringUUID(), pos, action, e);
			// Fail-closed: при внутренней ошибке гасим действие и очищаем сессию вместо vanilla-bypass.
			return failClosed(player, level, pos);
		}
	}

	// ------------------------------------------------------------------
	// START / STOP / ABORT
	// ------------------------------------------------------------------

	private static boolean onStart(ServerPlayer player, ServerLevel level, BlockPos pos) {
		UUID id = player.getUUID();

		// Границы/загрузка проверяются ДО чтения BlockState.
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
			SESSIONS.remove(id);
			return true;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			SESSIONS.remove(id);
			return true;
		}

		// Повторный START по той же цели: НЕ сбрасываем таймер, но перепроверяем сессию
		// (инструмент с компонентами, то же состояние блока, дистанция, права, hot/filled).
		BreakSession existing = SESSIONS.get(id);
		if (existing != null && existing.pos().equals(pos) && existing.dimension().equals(level.dimension())) {
			if (validate(existing, player, level, pos) == AbortReason.OK) {
				return true;
			}
			SESSIONS.remove(id);
			resyncState(player, level, pos);
			return true;
		}
		// Начата новая цель — старая сессия отменяется (как в ванильном gamemode).
		SESSIONS.remove(id);

		BlockBreakRules.Category category = BlockBreakRules.classify(level, pos, state);
		if (category == BlockBreakRules.Category.UNCLASSIFIED) {
			// Не покрыто правилами (в т.ч. неразрушимое) — обычное ванильное поведение и гейты.
			return false;
		}

		// Покрытый блок: все серверные гейты доступа обязательны (миксин отменил ванильные).
		if (!hasBreakPermission(player, level, pos) || isUnbreakable(state, level, pos)) {
			resync(player, level, pos, state);
			return true;
		}

		ItemStack tool = player.getMainHandItem();
		BlockBreakRules.ToolKind toolKind = BlockBreakRules.classifyTool(tool);
		BlockBreakPolicy.Decision decision = BlockBreakPolicy.evaluateStart(category, toolKind);
		if (decision != BlockBreakPolicy.Decision.ALLOW) {
			refuse(player, level, pos, state, category, decision, toolKind);
			return true;
		}
		// Наполненные станции/ёмкости не снимаем (перепроверка в момент старта).
		if (BlockBreakRules.isFilled(level, pos, state)) {
			refuse(player, level, pos, state, category, BlockBreakPolicy.Decision.DENY_FILLED, toolKind);
			return true;
		}

		// Creative: таймер 0, durability не расходуется, но проверки и однократность commit сохранены.
		if (player.isCreative()) {
			commit(player, level, pos);
			return true;
		}

		int requiredTicks = BlockBreakPolicy.requiredTicks(category, toolKind);
		BreakSession session = new BreakSession(id, level.dimension(), pos.immutable(), tool.copy(), state,
				category, toolKind, gameTime(level), requiredTicks);
		SESSIONS.put(id, session);
		level.destroyBlockProgress(player.getId(), pos, 0);
		return true;
	}

	private static boolean onStop(ServerPlayer player, ServerLevel level, BlockPos pos) {
		UUID id = player.getUUID();
		BreakSession session = SESSIONS.get(id);
		if (session == null) {
			// STOP без сессии: либо блок не покрыт, либо это предсказание клиента после отказа.
			if (!level.isLoaded(pos)) {
				return true;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				return true;
			}
			BlockBreakRules.Category category = BlockBreakRules.classify(level, pos, state);
			if (category == BlockBreakRules.Category.UNCLASSIFIED) {
				return false;
			}
			if (!hasBreakPermission(player, level, pos) || isUnbreakable(state, level, pos)) {
				resync(player, level, pos, state);
				return true;
			}
			ItemStack tool = player.getMainHandItem();
			BlockBreakRules.ToolKind toolKind = BlockBreakRules.classifyTool(tool);
			BlockBreakPolicy.Decision decision = BlockBreakPolicy.evaluateStart(category, toolKind);
			if (BlockBreakRules.isFilled(level, pos, state)) {
				refuse(player, level, pos, state, category, BlockBreakPolicy.Decision.DENY_FILLED, toolKind);
			} else if (decision != BlockBreakPolicy.Decision.ALLOW) {
				refuse(player, level, pos, state, category, decision, toolKind);
			} else {
				// Разрешённый блок, но сессии нет (например, creative уже закоммитил) — откатываем предсказание.
				resync(player, level, pos, state);
			}
			return true;
		}

		if (!session.pos().equals(pos)) {
			SESSIONS.remove(id);
			return true;
		}

		AbortReason reason = validate(session, player, level, pos);
		if (reason != AbortReason.OK) {
			SESSIONS.remove(id);
			devLogAbort(player, session, reason);
			return true;
		}
		if (session.ready(gameTime(level))) {
			commit(player, level, pos);
		}
		// Иначе — преждевременный STOP (предсказание клиента): сессию сохраняем, серверный тик доведёт.
		return true;
	}

	private static boolean onAbort(ServerPlayer player, ServerLevel level, BlockPos pos) {
		UUID id = player.getUUID();
		if (SESSIONS.remove(id) != null) {
			return true;
		}
		// Сессии нет: для покрытых блоков гасим ванильное действие, для остальных — обычное поведение.
		if (!level.isLoaded(pos)) {
			return true;
		}
		BlockState state = level.getBlockState(pos);
		return state.isAir() || BlockBreakRules.classify(level, pos, state) != BlockBreakRules.Category.UNCLASSIFIED;
	}

	// ------------------------------------------------------------------
	// Серверный тик
	// ------------------------------------------------------------------

	/** Тик всех активных сессий; вызывается из единого серверного тика мода. */
	public static void tickAll(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		try {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					tickPlayer(player);
				} catch (RuntimeException e) {
					WhiteFog.LOGGER.error("White Fog: break tick failed for player {}", player.getStringUUID(), e);
				}
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: break tick pass failed", e);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		BreakSession session = SESSIONS.get(id);
		if (session == null) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos pos = session.pos();
		if (!level.dimension().equals(session.dimension())) {
			SESSIONS.remove(id);
			return;
		}
		AbortReason reason = validate(session, player, level, pos);
		if (reason != AbortReason.OK) {
			SESSIONS.remove(id);
			devLogAbort(player, session, reason);
			return;
		}
		long now = gameTime(level);
		if (session.ready(now)) {
			commit(player, level, pos);
			return;
		}
		// Показываем серверный прогресс разрушения (визуальный crack), а не клиентский.
		if (session.requiredTicks() > 0) {
			int stage = (int) Math.min(9L, (now - session.startTick()) * 10L / session.requiredTicks());
			level.destroyBlockProgress(player.getId(), pos, stage);
		}
	}

	// ------------------------------------------------------------------
	// Валидация
	// ------------------------------------------------------------------

	private static AbortReason validate(BreakSession session, ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!player.isAlive() || player.isRemoved()) {
			return AbortReason.DEAD;
		}
		if (!level.isLoaded(pos)) {
			return AbortReason.CHUNK_UNLOADED;
		}
		if (level.isOutsideBuildHeight(pos)) {
			return AbortReason.OUT_OF_BOUNDS;
		}
		if (!player.level().dimension().equals(session.dimension())) {
			return AbortReason.DIMENSION_CHANGED;
		}
		if (!hasBreakPermission(player, level, pos)) {
			return AbortReason.NO_PERMISSION;
		}
		if (!ItemStack.isSameItemSameComponents(player.getMainHandItem(), session.tool())) {
			return AbortReason.TOOL_CHANGED;
		}
		BlockState now = level.getBlockState(pos);
		if (now.isAir()) {
			return AbortReason.BLOCK_CHANGED;
		}
		if (isUnbreakable(now, level, pos)) {
			return AbortReason.UNBREAKABLE;
		}
		if (BlockBreakRules.isHot(now)) {
			return AbortReason.HOT;
		}
		if (!now.equals(session.startState())) {
			return AbortReason.BLOCK_CHANGED;
		}
		// Наполненность перепроверяется динамически: содержимое печи можно добавить за время
		// таймера без смены BlockState, поэтому нельзя полагаться на категорию из START.
		if (BlockBreakRules.isFilled(level, pos, now)) {
			return AbortReason.FILLED;
		}
		if (!player.isWithinBlockInteractionRange(pos, WhiteFogConfig.BREAK_INTERACTION_RANGE_MARGIN)) {
			return AbortReason.OUT_OF_RANGE;
		}
		return AbortReason.OK;
	}

	// ------------------------------------------------------------------
	// Commit
	// ------------------------------------------------------------------

	/**
	 * Единственная точка изменения блока. Повторный вызов (второй пакет/второй игрок/второй
	 * обработчик) видит уже изменённый блок и не выдаёт второй предмет. Перечитывает фактическое
	 * состояние и ВСЕ разрешения (права, границы, горячее, наполненность, инструмент, дистанцию).
	 */
	private static void commit(ServerPlayer player, ServerLevel level, BlockPos pos) {
		SESSIONS.remove(player.getUUID());
		try {
			if (!player.isAlive() || player.isRemoved()) {
				return;
			}
			if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
				return;
			}
			if (!hasBreakPermission(player, level, pos)) {
				resyncState(player, level, pos);
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				// Блок уже изменён кем-то другим — второй commit подавлен.
				return;
			}
			BlockBreakRules.Category category = BlockBreakRules.classify(level, pos, state);
			if (category == BlockBreakRules.Category.UNCLASSIFIED || isUnbreakable(state, level, pos)) {
				resync(player, level, pos, state);
				return;
			}
			if (BlockBreakRules.isHot(state)) {
				return;
			}
			if (BlockBreakRules.isFilled(level, pos, state)) {
				return;
			}
			if (!player.isWithinBlockInteractionRange(pos, WhiteFogConfig.BREAK_INTERACTION_RANGE_MARGIN)) {
				return;
			}
			BlockBreakRules.ToolKind toolKind = BlockBreakRules.classifyTool(player.getMainHandItem());
			if (BlockBreakPolicy.evaluateStart(category, toolKind) != BlockBreakPolicy.Decision.ALLOW) {
				return;
			}
			if (category.isStation()) {
				StationRemoval.commit(level, pos, player);
			} else {
				commitNormal(level, pos, state, player, category);
			}
		} finally {
			level.destroyBlockProgress(player.getId(), pos, -1);
		}
	}

	/** Разрушение обычного блока: снятие без ванильного гейта «правильный инструмент», затем дроп. */
	private static void commitNormal(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
			BlockBreakRules.Category category) {
		ItemStack tool = player.getMainHandItem();
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		// Спец-логика блока (двойные растения и т.п.) — как в ванильном destroyBlock.
		BlockState after = state.getBlock().playerWillDestroy(level, pos, state, player);
		List<ItemStack> drops = BlockBreakPolicy.dropsZero(category)
				? List.of()
				: Block.getDrops(after, level, pos, blockEntity, player, tool);
		// recursionLeft = 512 — как в ванильных вызовах destroyBlock (взрывы/ломание), полные соседние апдейты.
		boolean removed = level.destroyBlock(pos, false, player, 512);
		if (!removed) {
			return;
		}
		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) {
				Block.popResource(level, pos, drop);
			}
		}
	}

	// ------------------------------------------------------------------
	// Разрешения (дублируют ванильные гейты handleBlockBreakAction)
	// ------------------------------------------------------------------

	/**
	 * Серверные гейты доступа к блоку, которые ванильный {@code handleBlockBreakAction}
	 * проверяет до начала прогресса: не спектатор, {@code mayInteract}, spawn protection,
	 * {@code blockActionRestricted} (adventure/без прав).
	 * (javap: {@code Level#mayInteract}, {@code MinecraftServer#isUnderSpawnProtection},
	 * {@code Player#blockActionRestricted}.)
	 */
	private static boolean hasBreakPermission(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (player.isSpectator()) {
			return false;
		}
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
			return false;
		}
		if (!level.mayInteract(player, pos)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (server != null && server.isUnderSpawnProtection(level, pos, player)) {
			return false;
		}
		return !player.blockActionRestricted(level, pos, player.gameMode());
	}

	/** Неразрушимый блок: {@code getDestroySpeed < 0} (bedrock, end_portal_frame, portal и т.п.). */
	private static boolean isUnbreakable(BlockState state, ServerLevel level, BlockPos pos) {
		return state.getDestroySpeed(level, pos) < 0.0F;
	}

	// ------------------------------------------------------------------
	// Отказ и очистка
	// ------------------------------------------------------------------

	private static void refuse(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
			BlockBreakRules.Category category, BlockBreakPolicy.Decision decision,
			BlockBreakRules.ToolKind toolKind) {
		RefuseReason reason = switch (decision) {
			case DENY_HOT -> RefuseReason.HOT;
			case DENY_FILLED -> RefuseReason.FILLED;
			default -> RefuseReason.NO_TOOL;
		};
		// Подсказку об «слишком крепко» показываем только для запрета по инструменту (утверждено ТЗ).
		if (decision == BlockBreakPolicy.Decision.DENY_NO_TOOL) {
			sendRefusalMessage(player, level, reason);
		}
		devLogRefusal(player, pos, category, decision, toolKind);
		// Откатываем клиентское предсказание блока и возможный предсказанный урон инструмента.
		resync(player, level, pos, state);
	}

	private static void sendRefusalMessage(ServerPlayer player, ServerLevel level, RefuseReason reason) {
		long now = gameTime(level);
		Map<RefuseReason, Long> perPlayer = LAST_MESSAGE.computeIfAbsent(player.getUUID(),
				key -> new EnumMap<>(RefuseReason.class));
		Long last = perPlayer.get(reason);
		if (last == null || now - last >= WhiteFogConfig.BREAK_REFUSE_MESSAGE_COOLDOWN_TICKS) {
			perPlayer.put(reason, now);
			player.sendSystemMessage(MESSAGE_TOO_HARD, true);
		}
	}

	/** Fail-closed: очищаем сессию, по возможности откатываем блок и гасим ванильное действие. */
	private static boolean failClosed(ServerPlayer player, ServerLevel level, BlockPos pos) {
		try {
			SESSIONS.remove(player.getUUID());
			resyncState(player, level, pos);
		} catch (RuntimeException secondary) {
			WhiteFog.LOGGER.error("White Fog: failed to fail-closed break action for {}",
					player.getStringUUID(), secondary);
		}
		// Всегда отменяем ванильное действие: безопасный отказ вместо возможного обхода.
		return true;
	}

	/** Возврат клиенту реального состояния блока и инвентаря (снимает предсказание и ghost durability). */
	private static void resync(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
		player.inventoryMenu.sendAllDataToRemote();
	}

	/** Безопасный ресинк по позиции (читает актуальное состояние, если чанк загружен). */
	private static void resyncState(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos)) {
			resync(player, level, pos, level.getBlockState(pos));
		}
	}

	/** Очистка сессии при disconnect/respawn/смене измерения. */
	public static void clearSession(ServerPlayer player) {
		if (player != null) {
			SESSIONS.remove(player.getUUID());
		}
	}

	/** Полная очистка состояния игрока (disconnect). */
	public static void clearPlayer(ServerPlayer player) {
		if (player != null) {
			UUID id = player.getUUID();
			SESSIONS.remove(id);
			LAST_MESSAGE.remove(id);
		}
	}

	// ------------------------------------------------------------------
	// Диагностика (только dev)
	// ------------------------------------------------------------------

	private static void devLogRefusal(ServerPlayer player, BlockPos pos, BlockBreakRules.Category category,
			BlockBreakPolicy.Decision decision, BlockBreakRules.ToolKind toolKind) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			WhiteFog.LOGGER.info("White Fog [break-refuse] player={} pos={} category={} decision={} tool={}",
					player.getStringUUID(), pos, category, decision, toolKind);
		}
	}

	private static void devLogAbort(ServerPlayer player, BreakSession session, AbortReason reason) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			WhiteFog.LOGGER.info("White Fog [break-abort] player={} pos={} reason={}",
					player.getStringUUID(), session.pos(), reason);
		}
	}

	/** Игровое время сервера (в 26.2 {@code getGameTime} живёт в {@code LevelData}). */
	private static long gameTime(ServerLevel level) {
		return level.getLevelData().getGameTime();
	}
}

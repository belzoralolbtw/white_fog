package com.whitefog.client.mixin;

import com.whitefog.WhiteFogConfig;
import com.whitefog.breaking.BlockBreakPolicy;
import com.whitefog.breaking.BlockBreakRules;
import com.whitefog.breaking.BreakTimerService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Клиентский гейт ванильного предсказания разрушения для контролируемых блоков
 * (hotfix / regression fix / swing fix этапа 1.3).
 *
 * <p><b>Регрессия (жалоба пользователя).</b> Первая версия hotfix'а отменяла на клиенте
 * только решения {@code DENY_*} ({@link BlockBreakPolicy.Decision}). Для разрешённых
 * категорий ({@code ALLOW}, например земля рукой) ванильное предсказание продолжало
 * работать: клиент быстро «ломал» блок локально и слал {@code START}/{@code STOP};
 * серверный таймер коммитил только позже, сервер откатывал блок — цикл «сломался →
 * откатилось» повторялся. Плюс для {@code DENY_*} подавленный {@code START} не доходил
 * до сервера, поэтому сервер не мог показать подсказку «Слишком крепко — нужен инструмент».</p>
 *
 * <p><b>Что делает эта версия.</b> Для ВСЕХ контролируемых категорий
 * ({@code BlockBreakRules.classify != UNCLASSIFIED}) ванильное предсказание подавляется и на
 * {@link MultiPlayerGameMode#startDestroyBlock}, и на {@link MultiPlayerGameMode#continueDestroyBlock}
 * ({@code @Inject at HEAD, cancellable = true}): тело ванильного метода не выполняется, поэтому нет
 * crack/прогресса/локального удаления и нет исходящих {@code START}/{@code STOP} от ванильной
 * логики. Тогда:
 * <ul>
 *     <li>{@code ALLOW} — клиент сам шлёт ровно один {@code START_DESTROY_BLOCK} через ванильный
 *         {@link #startPrediction} (корректная sequence) и один {@code ABORT_DESTROY_BLOCK} при
 *         отпускании ЛКМ / смене цели / смене инструмента. Серверный таймер этапа 1.3 стартует и
 *         доводит разрушение сам (прогресс-трещины рисует сервер пакетами
 *         {@code ClientboundBlockDestructionPacket} → {@code ClientPacketListener#handleBlockDestruction}
 *         → {@code ClientLevel#destroyBlockProgress}). {@link MultiPlayerGameMode#continueDestroyBlock}
 *         для {@code ALLOW} возвращает {@code true}: вызывающий {@code Minecraft#continueAttack}
 *         выполняет {@code ClientLevel#addBreakingBlockEffect} (частицы удара) и
 *         {@code LocalPlayer#swing} (ванильная cadence анимации удара). Тело метода всё равно
 *         пропущено, поэтому ванильного прогресса/локального удаления/пакетов {@code START}/{@code STOP}
 *         нет.</li>
 *     <li>{@code DENY_*} — {@code continueDestroyBlock} возвращает {@code false} (свинга и частиц
 *         нет); {@code START} НЕ отправляется вовсе; отказ показывается
 *         ЛОКАЛЬНО в action bar ({@code LocalPlayer#sendOverlayMessage}, javap-проверено)
 *         с cooldown {@link WhiteFogConfig#BREAK_REFUSE_MESSAGE_COOLDOWN_TICKS} тиков на причину.
 *         Серверу подсказка не нужна: о запросе он не знает, C2S-пакет только ради текста не шлём.</li>
 * </ul>
 * {@code UNCLASSIFIED} остаётся полностью ванильным (в т.ч. исходящие пакеты и локальное
 * предсказание).</p>
 *
 * <p><b>Визуальный отклик {@code ALLOW} (swing fix).</b> Возврат {@code true} из
 * {@code continueDestroyBlock} НЕ возвращает ванильный таймер/предсказание — он лишь разрешает
 * вызывающему выполнить свою пост-обработку. По javap {@code Minecraft#continueAttack} при
 * {@code true} делает ровно два вызова: {@code ClientLevel#addBreakingBlockEffect(pos, dir)}
 * (только {@code TerrainParticle}, блок не меняется) и {@code LocalPlayer#swing(MAIN_HAND)}.
 * {@code LocalPlayer#swing} вдобавок отправляет ванильный {@code ServerboundSwingPacket} (сервер:
 * {@code ServerGamePacketListenerImpl#handleAnimate} → {@code resetLastActionTime()} +
 * {@code swing(hand)}, без влияния на добычу) — это стандартная ванильная cadence майнинга,
 * а не {@code START}/{@code STOP} и не «спам» модовых пакетов. Первый клик анимируется и без
 * изменений: {@code Minecraft#startAttack} значение {@code startDestroyBlock} игнорирует
 * ({@code pop}) и в конце безусловно выполняет {@code player.swing(MAIN_HAND)} (ванильное
 * поведение, мод его не подавляет).</p>
 *
 * <p><b>Почему ручной {@code START}, а не «не подавлять» {@code startDestroyBlock}.</b>
 * Оставить ванильный {@code startDestroyBlock} нельзя: он отправляет {@code START} и ведёт
 * локальный прогресс, а {@code continueDestroyBlock} при завершении применяет локальное удаление
 * и шлёт {@code STOP}. Поэтому используем проверенный паттерн Fabric API: тень ванильного
 * приватного {@code startPrediction(ClientLevel, PredictiveAction)} и ручная отправка пакета с
 * корректной sequence.</p>
 *
 * <p><b>Разделение клиент/сервер.</b> Класс лежит в client-наборе и ссылается только на
 * клиентские типы; {@code common} не получает ссылок на {@code net.minecraft.client.*}.
 * {@link BlockBreakRules#classify} принимает {@link net.minecraft.world.level.Level}, поэтому
 * клиент и сервер принимают одно и то же решение. Текст подсказки берётся из общего
 * {@link BreakTimerService#MESSAGE_TOO_HARD} (единый источник, серверный текст не меняется).</p>
 *
 * <p><b>API evidence (javap по реальным 26.2 deobf jar):</b>
 * {@code MultiPlayerGameMode} — {@code public boolean startDestroyBlock(BlockPos, Direction)},
 * {@code public boolean continueDestroyBlock(BlockPos, Direction)}, {@code public void stopDestroyBlock()},
 * {@code private void startPrediction(ClientLevel, PredictiveAction)}, приватные поля
 * {@code private final Minecraft minecraft}, {@code private final ClientPacketListener connection};
 * {@code ServerboundPlayerActionPacket(Action, BlockPos, Direction, int)} и
 * {@code (Action, BlockPos, Direction)} (sequence = 0);
 * {@code LocalPlayer#sendOverlayMessage(Component)} → {@code ChatListener.handleOverlay} (action bar);
 * {@code Entity#tickCount} (public int) для cooldown; {@code ClientLevel#getLevelData().getGameTime()}
 * (не используется — cooldown на tickCount).</p>
 *
 * <p><b>API evidence вызывающего (javap, clientonly deobf 26.2):</b>
 * {@code Minecraft#continueAttack(boolean)} — для блока вызывает {@code gameMode.continueDestroyBlock(pos, dir)}
 * и ТОЛЬКО при {@code true} делает {@code level.addBreakingBlockEffect(pos, dir)} + {@code player.swing(MAIN_HAND)}
 * (при {@code false} — немедленный {@code return}); {@code Minecraft#startAttack()} значение
 * {@code startDestroyBlock} сбрасывает через {@code pop} и в хвосте безусловно вызывает
 * {@code player.swing(MAIN_HAND)}; {@code Minecraft#handleKeybinds()} вызывает {@code startAttack()}
 * на {@code keyAttack.consumeClick()} и {@code continueAttack(...)} каждый тик при зажатой ЛКМ;
 * {@code ClientLevel#addBreakingBlockEffect(BlockPos, Direction)} создаёт только {@code TerrainParticle};
 * {@code LocalPlayer#swing(InteractionHand)} = {@code super.swing(hand)} + {@code ServerboundSwingPacket};
 * {@code ServerGamePacketListenerImpl#handleAnimate(ServerboundSwingPacket)} = {@code resetLastActionTime()} +
 * {@code player.swing(hand)} (без геймплейных эффектов); {@code ClientPacketListener#handleBlockDestruction}
 * → {@code ClientLevel#destroyBlockProgress} (серверные трещины не зависят от нашего миксина).</p>
 *
 * <p><b>Источники-референсы (паттерн прочитан, адаптирован, не скопирован):</b></p>
 * <ul>
 *     <li>{@code FabricMC/fabric-api} ({@code 26.2}, {@code fabric-events-interaction-v0}) —
 *         {@code src/client/java/net/fabricmc/fabric/mixin/event/interaction/client/MultiPlayerGameModeMixin.java}:
 *         {@code @Shadow @Final ClientPacketListener connection} и
 *         {@code @Shadow protected abstract void startPrediction(ClientLevel, PredictiveAction)},
 *         ручная отправка {@code new ServerboundPlayerActionPacket(START_DESTROY_BLOCK, pos, direction, id)}.</li>
 *     <li>{@code MeteorDevelopment/meteor-client} — {@code MultiPlayerGameModeMixin.java}:
 *         тот же {@code startPrediction(level, sequence -> new ServerboundPlayerActionPacket(...))}.</li>
 *     <li>{@code Zergatul/cheatutils} ({@code 26.2}) — {@code modules/hacks/AreaMine.java},
 *         {@code BedrockBreaker.java}: ручная работа с {@code BlockStatePredictionHandler}
 *         / sequence на этой же версии MC.</li>
 *     <li>{@code Wynntils/Wynntils} — {@code MultiPlayerGameModeMixin.java}: точный паттерн
 *         {@code @Inject(... at = HEAD, cancellable = true)} → {@code cir.setReturnValue(false)}.</li>
 *     <li><b>Swing fix — визуальный отклик.</b> {@code Fabricators-of-Create/Porting-Lib}
 *         ({@code 1.21.1}) — {@code modules/base/.../mixin/client/MinecraftMixin.java}:
 *         {@code @WrapOperation(method="continueAttack", at=INVOKE continueDestroyBlock)}; явный комментарий
 *         вызывающего — «true -> ... do swing and crack; false -> ... do NOT swing or crack» (наш принцип).
 *         {@code Wurst-Imperium/Wurst7} — {@code hacks/AutoMineHack.java}, {@code NukerLegitHack.java}:
 *         {@code if (im.continueDestroyBlock(pos, side)) { MC.level.addBreakingBlockEffect(pos, side);
 *         MC.player.swing(MAIN_HAND); }} — точный OSS-образец пары «частицы + свинг» при майнинге.
 *         {@code KiltMC/Kilt} — {@code MultiPlayerGameModeInject.java}: {@code @Inject(continueDestroyBlock,
 *         HEAD, cancellable)} → {@code cir.setReturnValue(true)} как разрешение ванильного визуала.
 *         {@code Aspw-w/Krs} — {@code MinecraftClientMixin.java} ({@code swingHandWithoutPacket(...)}) —
 *         рассмотренная альтернатива «локальный свинг без пакета», сознательно НЕ выбрана
 *         (см. раздел про визуальный отклик {@code ALLOW}).</li>
 * </ul>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	/** Клиент, владеющий этим gamemode; нужен для доступа к локальному игроку и уровню. */
	@Shadow
	@Final
	private Minecraft minecraft;

	/** Соединение клиента: для ручного {@code ABORT_DESTROY_BLOCK} (как ванильный {@code stopDestroyBlock}). */
	@Shadow
	@Final
	private ClientPacketListener connection;

	/**
	 * Приватный ванильный хелпер предсказания. Shadowing как {@code protected abstract} — ровно тот же
	 * приём, что и в Fabric API (см. Javadoc класса): сам получает sequence из
	 * {@code BlockStatePredictionHandler} и сам отправляет пакет.
	 */
	@Shadow
	protected abstract void startPrediction(ClientLevel clientLevel, PredictiveAction predictiveAction);

	// ------------------------------------------------------------------
	// Клиентское состояние контролируемой сессии
	// ------------------------------------------------------------------

	/** Позиция блока, для которой мод уже отправил серверу {@code START} (null — сессии нет). */
	@Unique
	private BlockPos whiteFog$serverPos;

	/** Уровень (мир/измерение) на момент {@code START}: смена мира должна пересоздать сессию. */
	@Unique
	private ClientLevel whiteFog$serverLevel;

	/** Направление, с которым был отправлен {@code START} (нужно для {@code ABORT}). */
	@Unique
	private Direction whiteFog$serverDir;

	/** Копия инструмента на момент {@code START}: смена инструмента должна пересоздать серверную сессию. */
	@Unique
	private ItemStack whiteFog$serverTool;

	/** Тик последней показанной подсказки, на причину отказа (cooldown 20 тиков на причину). */
	@Unique
	private Map<BlockBreakPolicy.Decision, Integer> whiteFog$refuseTicks;

	// ------------------------------------------------------------------
	// Инъекции
	// ------------------------------------------------------------------

	/**
	 * Начало разрушения. Для контролируемого блока подавляем ванильную логику целиком:
	 * {@code ALLOW} → вручную шлём {@code START}, {@code DENY_*} → показываем локальную подсказку.
	 * {@code UNCLASSIFIED} не трогаем.
	 */
	@Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
			at = @At("HEAD"), cancellable = true)
	private void whiteFog$onStartDestroyBlock(BlockPos pos, Direction direction,
			CallbackInfoReturnable<Boolean> cir) {
		BlockBreakPolicy.Decision decision = whiteFog$decisionOrNull(pos);
		if (decision == null) {
			// Не покрыто правилами (или нет мира/игрока) — обычное ванильное поведение.
			return;
		}
		// Контролируемый блок: ванильного предсказания быть не должно ни при каком решении.
		whiteFog$clearServerSession();
		if (decision == BlockBreakPolicy.Decision.ALLOW) {
			whiteFog$beginServerSession(pos, direction);
		} else {
			whiteFog$maybeShowRefusal(decision);
		}
		// Возврат false: ванильное тело startDestroyBlock не выполняется (нет START-предсказания
		// и локального прогресса). Значение возврата здесь несущественно: Minecraft#startAttack
		// сбрасывает его через pop и свинг выполняет сам в хвосте метода. Визуал (swing/частицы)
		// для ALLOW дальше обеспечивает ветка continueDestroyBlock.
		cir.setReturnValue(false);
	}

	/**
	 * Продолжение разрушения. Для контролируемого блока ванильное тело подавляем всегда.
	 * Для {@code ALLOW} поддерживаем серверную сессию (при смене цели/инструмента шлём
	 * {@code ABORT}+{@code START}, иначе ничего не делаем — серверный таймер уже идёт) и
	 * возвращаем {@code true}, чтобы вызывающий {@code Minecraft#continueAttack} выполнил
	 * ванильный визуал удара (частицы + {@code player.swing}).
	 * Для {@code DENY_*} возвращаем {@code false} (без визуала) и показываем только локальную
	 * подсказку с cooldown. {@code UNCLASSIFIED} оставляем ванили
	 * (если до этого была наша сессия — аккуратно её закрываем).
	 */
	@Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
			at = @At("HEAD"), cancellable = true)
	private void whiteFog$onContinueDestroyBlock(BlockPos pos, Direction direction,
			CallbackInfoReturnable<Boolean> cir) {
		BlockBreakPolicy.Decision decision = whiteFog$decisionOrNull(pos);
		if (decision == null) {
			// UNCLASSIFIED: если мод вёл свою сессию, закрываем её и отдаём блок ванили.
			whiteFog$clearServerSession();
			return;
		}
		if (decision == BlockBreakPolicy.Decision.ALLOW) {
			if (!whiteFog$sessionMatches(pos)) {
				// Смена цели или инструмента: сервер не пересоздаст сессию сам, нужен ABORT+START.
				whiteFog$clearServerSession();
				whiteFog$beginServerSession(pos, direction);
			}
			// true → Minecraft#continueAttack выполнит addBreakingBlockEffect (частицы) и
			// player.swing (ванильная cadence анимации удара). Тело continueDestroyBlock
			// пропущено (мы на HEAD, cancellable), поэтому ванильного прогресса/локального
			// удаления/пакетов START/STOP нет; серверный таймер не затрагивается.
			cir.setReturnValue(true);
			return;
		}
		// DENY_*: без свинга/частиц (false), START не шлём, только локальная подсказка.
		whiteFog$clearServerSession();
		whiteFog$maybeShowRefusal(decision);
		cir.setReturnValue(false);
	}

	/**
	 * Отпускание ЛКМ / потеря фокуса. Ванильный {@code stopDestroyBlock} шлёт {@code ABORT} только
	 * если {@code isDestroying}, а у нас для контролируемых блоков ванильное состояние не создаётся —
	 * поэтому {@code ABORT} отправляем вручную и очищаем сессию. Ванильную логику не отменяем:
	 * для {@code UNCLASSIFIED} она должна отработать как обычно.
	 */
	@Inject(method = "stopDestroyBlock", at = @At("HEAD"))
	private void whiteFog$onStopDestroyBlock(CallbackInfo ci) {
		whiteFog$clearServerSession();
	}

	// ------------------------------------------------------------------
	// Хелперы (client-only)
	// ------------------------------------------------------------------

	/**
	 * Решение политики для контролируемого блока или {@code null}, если блок не покрыт правилами
	 * (в т.ч. {@code UNCLASSIFIED}), мир/игрок недоступны или чанк не загружен. В случае {@code null}
	 * мод не вмешивается.
	 */
	@Unique
	private BlockBreakPolicy.Decision whiteFog$decisionOrNull(BlockPos pos) {
		Minecraft client = this.minecraft;
		if (client == null || pos == null) {
			return null;
		}
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || !level.isLoaded(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		BlockBreakRules.Category category = BlockBreakRules.classify(level, pos, state);
		if (category == BlockBreakRules.Category.UNCLASSIFIED) {
			return null;
		}
		BlockBreakRules.ToolKind toolKind = BlockBreakRules.classifyTool(player.getMainHandItem());
		return BlockBreakPolicy.evaluateStart(category, toolKind);
	}

	/** Начинает серверную сессию: ровно один {@code START} с корректной sequence + запоминание цели/инструмента. */
	@Unique
	private void whiteFog$beginServerSession(BlockPos pos, Direction direction) {
		ClientLevel level = this.minecraft.level;
		LocalPlayer player = this.minecraft.player;
		if (level == null || player == null) {
			return;
		}
		BlockPos immutable = pos.immutable();
		// startPrediction даёт валидную sequence и отправляет пакет от нашего имени.
		// Локальное состояние блока не меняем (лямбда только строит пакет) — предсказания нет.
		startPrediction(level, sequence -> new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, immutable, direction, sequence));
		this.whiteFog$serverPos = immutable;
		this.whiteFog$serverLevel = level;
		this.whiteFog$serverDir = direction;
		this.whiteFog$serverTool = player.getMainHandItem().copy();
	}

	/** Совпадает ли текущая цель, уровень и инструмент с уже отправленной серверной сессией. */
	@Unique
	private boolean whiteFog$sessionMatches(BlockPos pos) {
		if (this.whiteFog$serverPos == null || !this.whiteFog$serverPos.equals(pos)) {
			return false;
		}
		if (this.minecraft == null || this.minecraft.level != this.whiteFog$serverLevel) {
			// Смена мира/измерения — старая сессия недействительна.
			return false;
		}
		if (this.whiteFog$serverTool == null || this.minecraft.player == null) {
			return false;
		}
		return ItemStack.isSameItemSameComponents(this.minecraft.player.getMainHandItem(), this.whiteFog$serverTool);
	}

	/** Шлёт {@code ABORT} для текущей серверной сессии (если есть) и очищает состояние. */
	@Unique
	private void whiteFog$clearServerSession() {
		BlockPos pos = this.whiteFog$serverPos;
		if (pos == null) {
			return;
		}
		Direction dir = this.whiteFog$serverDir == null ? Direction.DOWN : this.whiteFog$serverDir;
		ClientPacketListener conn = this.connection;
		if (conn != null) {
			// 3-арг. конструктор = sequence 0, как в ванильном stopDestroyBlock.
			conn.send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, dir));
		}
		this.whiteFog$serverPos = null;
		this.whiteFog$serverLevel = null;
		this.whiteFog$serverDir = null;
		this.whiteFog$serverTool = null;
	}

	/**
	 * Показывает локальную action-bar подсказку об отказе с cooldown на причину.
	 * Исходящий {@code START} для {@code DENY_*} не отправляется, поэтому сервер показать её не может.
	 */
	@Unique
	private void whiteFog$maybeShowRefusal(BlockBreakPolicy.Decision decision) {
		Minecraft client = this.minecraft;
		if (client == null || client.player == null) {
			return;
		}
		if (this.whiteFog$refuseTicks == null) {
			this.whiteFog$refuseTicks = new EnumMap<>(BlockBreakPolicy.Decision.class);
		}
		int now = client.player.tickCount;
		Integer last = this.whiteFog$refuseTicks.get(decision);
		if (last == null || now - last >= WhiteFogConfig.BREAK_REFUSE_MESSAGE_COOLDOWN_TICKS) {
			this.whiteFog$refuseTicks.put(decision, now);
			client.player.sendOverlayMessage(BreakTimerService.MESSAGE_TOO_HARD);
		}
	}
}

package com.whitefog.tests.breaktimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Чистая модель клиентского гейта предсказания (regression fix этапа 1.3).
 *
 * <p>Воспроизводит решение клиентского миксина {@code MultiPlayerGameModeMixin}
 * БЕЗ Minecraft: для контролируемых категорий ванильное предсказание подавляется
 * (аналог {@code setReturnValue} в cancellable HEAD); при {@code ALLOW} клиент шлёт ровно один
 * {@code START} и один {@code ABORT} при отпускании/смене цели/инструмента, а
 * {@code continueDestroyBlock} возвращает {@code true} (ванильный визуал удара: swing + частицы);
 * при {@code DENY_*} {@code START} не шлётся, возврат {@code false} (без визуала), а подсказка
 * показывается локально с cooldown {@code policy.messageCooldownTicks()} тиков на причину.</p>
 *
 * <p>Это НЕ runtime-proof: реальные пакеты, {@code ItemStack} и {@code BlockPos} здесь
 * не проверяются. Проверяется только логика решений/состояния и то, какой возврат получил бы
 * вызывающий {@code Minecraft}.</p>
 */
public final class ClientGate {
	/** {@code null} в {@link BlockKind} = UNCLASSIFIED (ваниль, мод не вмешивается). */
	private final BreakPolicy policy;

	private BlockKey serverPos;
	private ToolStack serverTool;
	private final Map<BreakPolicy.Decision, Long> lastMessageTick = new HashMap<>();
	private final List<String> sends = new ArrayList<>();
	private long tick;
	private int messages;
	private int vanillaSuppressed;
	private int visualContinueTicks;

	public ClientGate(BreakPolicy policy) {
		this.policy = policy;
	}

	/**
	 * Аналог {@code startDestroyBlock} на клиенте.
	 *
	 * @return {@code true}, если ванильное предсказание подавлено (контролируемая категория);
	 *         {@code false} — блок не покрыт правилами, мод не вмешивается.
	 */
	public boolean start(BlockKey pos, BlockKind kind, ToolStack tool) {
		tick++;
		if (kind == null) {
			return false;
		}
		vanillaSuppressed++;
		BreakPolicy.Decision decision = decision(kind, tool);
		clearSession();
		if (decision == BreakPolicy.Decision.ALLOW) {
			beginSession(pos, tool);
		} else {
			showRefusal(decision);
		}
		return true;
	}

	/**
	 * Аналог {@code continueDestroyBlock} на клиенте.
	 *
	 * @return {@code true}, если ванильное предсказание подавлено.
	 */
	public boolean continueTick(BlockKey pos, BlockKind kind, ToolStack tool) {
		tick++;
		if (kind == null) {
			// UNCLASSIFIED: закрываем нашу сессию и отдаём блок ванили.
			clearSession();
			return false;
		}
		vanillaSuppressed++;
		BreakPolicy.Decision decision = decision(kind, tool);
		if (decision == BreakPolicy.Decision.ALLOW) {
			if (!sessionMatches(pos, tool)) {
				// Смена цели/инструмента: сервер сам сессию не пересоздаст — нужен ABORT+START.
				clearSession();
				beginSession(pos, tool);
			}
			// Модель возврата true: вызывающий Minecraft#continueAttack выполнит
			// addBreakingBlockEffect (частицы) + player.swing (ванильная анимация удара).
			visualContinueTicks++;
		} else {
			clearSession();
			showRefusal(decision);
		}
		return true;
	}

	/**
	 * Модель возврата {@code continueDestroyBlock}: {@code true} только для контролируемого
	 * {@code ALLOW} (ванильный визуал удара). {@code DENY_*} → {@code false}; для
	 * {@code UNCLASSIFIED} мод не вмешивается вовсе (ваниль сама решает) → {@code false}.
	 */
	public boolean continueReturnsTrue(BlockKind kind, ToolStack tool) {
		if (kind == null) {
			return false;
		}
		return decision(kind, tool) == BreakPolicy.Decision.ALLOW;
	}

	/** Аналог {@code stopDestroyBlock}: ручной {@code ABORT} и очистка. */
	public void stop() {
		tick++;
		clearSession();
	}

	// ------------------------------------------------------------------
	// Наблюдаемое состояние (для проверок)
	// ------------------------------------------------------------------

	/** Отправленные модом пакеты в порядке отправки: {@code START@...}/{@code ABORT@...}. */
	public List<String> sends() {
		return List.copyOf(sends);
	}

	/** Сколько раз показана локальная action-bar подсказка. */
	public int messages() {
		return messages;
	}

	/** Сколько раз было подавлено ванильное предсказание (controlled-категории). */
	public int vanillaSuppressed() {
		return vanillaSuppressed;
	}

	/**
	 * Сколько continue-тиков контролируемый {@code ALLOW} отдал ванильному визуалу удара
	 * (модель возврата {@code true} → swing + частицы). У {@code DENY_*} и {@code UNCLASSIFIED} — 0.
	 */
	public int visualContinueTicks() {
		return visualContinueTicks;
	}

	/** Активна ли серверная сессия (мод уже отправил START и ещё не отправил ABORT). */
	public boolean hasServerSession() {
		return serverPos != null;
	}

	// ------------------------------------------------------------------
	// Внутреннее
	// ------------------------------------------------------------------

	private BreakPolicy.Decision decision(BlockKind kind, ToolStack tool) {
		return policy.evaluateStart(kind, tool, policy.classify(tool));
	}

	private void beginSession(BlockKey pos, ToolStack tool) {
		sends.add("START@" + pos);
		serverPos = pos;
		serverTool = tool;
	}

	private boolean sessionMatches(BlockKey pos, ToolStack tool) {
		return serverPos != null && serverPos.equals(pos) && serverTool != null && serverTool.equals(tool);
	}

	private void clearSession() {
		if (serverPos == null) {
			return;
		}
		sends.add("ABORT@" + serverPos);
		serverPos = null;
		serverTool = null;
	}

	private void showRefusal(BreakPolicy.Decision decision) {
		Long last = lastMessageTick.get(decision);
		long cooldown = policy.messageCooldownTicks();
		if (last == null || tick - last >= cooldown) {
			lastMessageTick.put(decision, tick);
			messages++;
		}
	}
}

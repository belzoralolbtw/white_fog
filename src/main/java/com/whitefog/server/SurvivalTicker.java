package com.whitefog.server;

import com.whitefog.state.PlayerSurvivalState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Логика серверного тика состояния выживания.
 *
 * <p>Этап 1.1 — фундамент: тик приводит значения в допустимые границы и корректно продвигает
 * длительные процессы по <b>фактически прошедшему игровому интервалу</b>
 * ({@code now - lastProcessedAt}). Никакого расхода ресурсов (голод/жажда/урон) здесь пока нет —
 * это последующие этапы; чтобы не смешивать, изменение значений не выполняется.</p>
 *
 * <p>Правило контракта: любой длительный процесс хранит {@code startedAt}, {@code lastProcessedAt}
 * и (вычисляемую) {@code duration}. Мы обрабатываем только реально прошедший интервал, поэтому
 * выгрузка/загрузка чанка и пауза сервера не «перескакивают» таймеры.</p>
 */
public final class SurvivalTicker {
	private SurvivalTicker() {
	}

	/** Обрабатывает одного игрока за один серверный тик. */
	public static void tick(MinecraftServer server, ServerPlayer player, PlayerSurvivalState state) {
		long now = server.getTickCount();

		// Защита от некорректных значений (например, после ручного редактирования NBT).
		state.normalize();

		// Дизентерия: уменьшаем оставшееся время ровно на прошедший интервал.
		if (state.getDysenteryRemainingTicks() > 0) {
			long elapsed = now - state.getDysenteryLastProcessedAtTick();
			if (elapsed > 0L) {
				long remaining = state.getDysenteryRemainingTicks() - elapsed;
				state.setDysenteryRemainingTicks((int) Math.max(0L, remaining));
				state.setDysenteryLastProcessedAtTick(now);
			}
		}

		// Отметки времени активных процессов (сон/работа) продвигаем только если процесс активен.
		if (state.isSleepActive()) {
			state.setSleepLastProcessedAtTick(now);
		}
		if (state.isWorkActive()) {
			state.setWorkLastProcessedAtTick(now);
		}
	}
}

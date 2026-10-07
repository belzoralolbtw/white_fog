package com.whitefog.tests.breaktimer;

/**
 * Действия протокола разрушения блока (аналог
 * {@code ServerboundPlayerActionPacket.Action} в MC 26.2).
 *
 * <p>В реальном моде это три ветки {@code ServerPlayerGameMode#handleBlockBreakAction}:
 * START_DESTROY_BLOCK / STOP_DESTROY_BLOCK / ABORT_DESTROY_BLOCK. Здесь они смоделированы
 * как независимый enum, чтобы прототип не зависел от Minecraft.</p>
 */
public enum BreakAction {
	START,
	STOP,
	ABORT
}

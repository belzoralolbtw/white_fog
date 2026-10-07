package com.whitefog;

import com.whitefog.state.PlayerSurvivalState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.server.level.ServerPlayer;

/**
 * Регистрация серверного персистентного состояния игрока через Fabric Data Attachment API v1.
 *
 * <p>Источники (адаптировано под Fabric 26.2, не скопировано дословно):</p>
 * <ul>
 *     <li>Fabric Docs, reference/26.1.2 —
 *         {@code src/main/java/com/example/docs/attachment/ExampleModAttachments.java}
 *         (шаблон {@code AttachmentRegistry.create(id, b -> b.initializer(...).persistent(...).copyOnDeath())}).</li>
 *     <li>Fabric API, {@code fabric-data-attachment-api-v1} —
 *         {@code net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl#transfer}
 *         (подтверждает семантику {@code copyOnDeath}: при респавне игрока/конвертации/возврате из End
 *         вложение переносится на новый экземпляр, иначе — только при {@code copyOnDeath()}).</li>
 * </ul>
 *
 * <p>Вложение хранится по одному на игрока (не в static-поле), сохраняется в playerdata
 * (переживает save/load и disconnect/reconnect), автоматически переносится при смерти и смене
 * измерения, поэтому второй компонент не создаётся.</p>
 */
public final class WhiteFogAttachments {
	/**
	 * Персистентное состояние выживания игрока.
	 * Инициализатор отдаёт безопасные значения по умолчанию, {@code copyOnDeath()} переносит
	 * состояние на нового игрока при респавне.
	 */
	public static final AttachmentType<PlayerSurvivalState> PLAYER_SURVIVAL = AttachmentRegistry.create(
			WhiteFog.id("player_survival"),
			builder -> builder
					.initializer(PlayerSurvivalState::createDefault)
					.persistent(PlayerSurvivalState.CODEC)
					.copyOnDeath());

	private WhiteFogAttachments() {
	}

	/**
	 * Явно инициализирует класс и тем самым регистрирует вложение.
	 * Вызывается из common initializer до старта сервера.
	 */
	public static void register() {
		// Обращение к PLAYER_SURVIVAL уже произошло при загрузке класса; метод нужен как явная точка вызова.
	}

	/** Возвращает состояние игрока, создавая его со значениями по умолчанию при отсутствии. */
	public static PlayerSurvivalState getOrCreate(ServerPlayer player) {
		return ((AttachmentTarget) player).getAttachedOrCreate(PLAYER_SURVIVAL);
	}

	/** Возвращает состояние игрока без создания (может быть {@code null}). Для команд/диагностики. */
	public static PlayerSurvivalState peek(ServerPlayer player) {
		return ((AttachmentTarget) player).getAttached(PLAYER_SURVIVAL);
	}

	/** Заменяет состояние игрока. */
	public static void set(ServerPlayer player, PlayerSurvivalState state) {
		((AttachmentTarget) player).setAttached(PLAYER_SURVIVAL, state);
	}
}

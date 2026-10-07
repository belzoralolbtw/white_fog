package com.whitefog.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.whitefog.WhiteFogAttachments;
import com.whitefog.state.PlayerSurvivalState;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Отладочная команда {@code /whitefog debug [player]} — только для dev-среды.
 *
 * <p>Печатает серверное состояние выбранного игрока и <b>не изменяет данные</b>:
 * используется {@link WhiteFogAttachments#peek(ServerPlayer)}, который не создаёт состояние,
 * если его ещё нет. Если состояния нет, показываются безопасные значения по умолчанию (только в выводе).</p>
 *
 * <p>Регистрируется в {@link com.whitefog.server.WhiteFogServer} только при
 * {@code FabricLoader.isDevelopmentEnvironment()}.</p>
 */
public final class WhiteFogDebugCommand {
	private WhiteFogDebugCommand() {
	}

	/** Регистрирует команду в диспетчере (сигнатура Fabric CommandRegistrationCallback). */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("whitefog")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("debug")
						.executes(WhiteFogDebugCommand::printSelf)
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> printTarget(ctx, EntityArgument.getPlayer(ctx, "player"))))));
	}

	private static int printSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return print(ctx.getSource(), ctx.getSource().getPlayerOrException());
	}

	private static int printTarget(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
		return print(ctx.getSource(), target);
	}

	private static int print(CommandSourceStack source, ServerPlayer target) {
		int now = source.getServer().getTickCount();
		String name = target.getName().getString();

		PlayerSurvivalState state = WhiteFogAttachments.peek(target);
		if (state == null) {
			source.sendSuccess(() -> Component.literal(
					"White Fog [" + name + "]: persistent state not created yet; safe defaults:"), false);
			PlayerSurvivalState defaults = PlayerSurvivalState.createDefault();
			source.sendSuccess(() -> Component.literal("  " + defaults.describe(now)), false);
			return 0;
		}

		source.sendSuccess(() -> Component.literal("White Fog [" + name + "]:"), false);
		source.sendSuccess(() -> Component.literal("  " + state.describe(now)), false);
		return 1;
	}
}

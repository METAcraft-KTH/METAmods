package nu.metacraft.lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.lib.util.helper.HardcoreHelper;

import java.util.Collection;

public class HardcoreSwitcherCommand {

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
				Commands.literal("hardcore").requires(Permissions.require("metacraft.hardcore", 2)).then(
						Commands.argument("state", BoolArgumentType.bool()).executes(context -> {
							boolean hardcore = BoolArgumentType.getBool(context, "state");
							if (context.getSource().getServer().isHardcore() == hardcore) {
								context.getSource().sendFailure(Component.literal("Hardcore mode is already " + (hardcore ? "enabled" : "disabled") + "!"));
								return 0;
							}
							if (HardcoreHelper.setHardcoreMode(context.getSource().getServer(), hardcore)) {
								context.getSource().sendSuccess(
										() -> Component.literal("Successfully " + (hardcore ? "enabled" : "disabled") + " hardcore mode."),
										true
								);
								return 1;
							} else {
								context.getSource().sendFailure(Component.literal("Unable to change hardcore mode state! Perhaps another mod breaks stuff?"));
								return 0;
							}
						})
				)
		);
		dispatcher.register(
				Commands.literal("fake-hardcore").requires(Permissions.require("metacraft.fake-hardcore", 2)).then(
						Commands.argument("state", BoolArgumentType.bool()).then(
								Commands.argument("player", EntityArgument.entities()).executes(context -> {
									boolean hardcore = BoolArgumentType.getBool(context, "state");
									Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "player");
									for (var player : players) {
										HardcoreHelper.sendHardcoreState(player, hardcore);
									}
									if (players.size() > 1) {
										context.getSource().sendSuccess(
												() -> Component.literal((hardcore ? "Enabled" : "Disabled") + " fake hardcore mode for " +
														players.size() + " players."),
												true
										);
									} else if (players.size() == 1) {
										context.getSource().sendSuccess(
												() -> Component.literal(
														(hardcore ? "Enabled" : "Disabled") + " fake hardcore mode for "
												).append(players.stream().findFirst().get().getName()),
												true
										);
									} else {
										context.getSource().sendSuccess(
												() -> Component.literal("But nothing happened..."),
												false
										);
									}
									return players.size();
								})
						)
				)
		);
	}

}

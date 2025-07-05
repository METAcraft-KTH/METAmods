package nu.metacraft.lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nu.metacraft.lib.util.helper.HardcoreHelper;

import java.util.Collection;

public class HardcoreSwitcherCommand {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				CommandManager.literal("hardcore").requires(Permissions.require("metacraft.hardcore", 2)).then(
						CommandManager.argument("state", BoolArgumentType.bool()).executes(context -> {
							boolean hardcore = BoolArgumentType.getBool(context, "state");
							if (context.getSource().getServer().isHardcore() == hardcore) {
								context.getSource().sendError(Text.literal("Hardcore mode is already " + (hardcore ? "enabled" : "disabled") + "!"));
								return 0;
							}
							if (HardcoreHelper.setHardcoreMode(context.getSource().getServer(), hardcore)) {
								context.getSource().sendFeedback(
										() -> Text.literal("Successfully " + (hardcore ? "enabled" : "disabled") + " hardcore mode."),
										true
								);
								return 1;
							} else {
								context.getSource().sendError(Text.literal("Unable to change hardcore mode state! Perhaps another mod breaks stuff?"));
								return 0;
							}
						})
				)
		);
		dispatcher.register(
				CommandManager.literal("fake-hardcore").requires(Permissions.require("metacraft.fake-hardcore", 2)).then(
						CommandManager.argument("state", BoolArgumentType.bool()).then(
								CommandManager.argument("player", EntityArgumentType.entities()).executes(context -> {
									boolean hardcore = BoolArgumentType.getBool(context, "state");
									Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(context, "player");
									for (var player : players) {
										HardcoreHelper.sendHardcoreState(player, hardcore);
									}
									if (players.size() > 1) {
										context.getSource().sendFeedback(
												() -> Text.literal((hardcore ? "Enabled" : "Disabled") + " fake hardcore mode for " +
														players.size() + " players."),
												true
										);
									} else if (players.size() == 1) {
										context.getSource().sendFeedback(
												() -> Text.literal(
														(hardcore ? "Enabled" : "Disabled") + " fake hardcore mode for "
												).append(players.stream().findFirst().get().getName()),
												true
										);
									} else {
										context.getSource().sendFeedback(
												() -> Text.literal("But nothing happened..."),
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

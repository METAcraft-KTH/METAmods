package nu.metacraft.info_commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	private static final Identifier afterDefault = Identifier.of("metacraftinfocommands:register-commands");

	public static void init() {
		CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, afterDefault);
		CommandRegistrationCallback.EVENT.register(afterDefault, (dispatcher, registryAccess, environment) -> {
			Info.getConfig().getCommands().forEach((command, node) -> {
				if (dispatcher.getRoot().getChild(command) == null) {
					dispatcher.register(addNode(command, node));
				} else {
					Info.LOGGER.error("/" + command + " is already registered by vanilla or another mod, skipping it!");
				}
			});
			if (Info.getConfig().enableResendCommandTreeCommand()) {
				dispatcher.register(literal("meta-info-resend-command-tree")
						.requires(Permissions.require("metacraft.meta-info-resend-command-tree", 4))
						.executes(ctx -> {
					Helper.resendCommandTreeToAllPlayers(ctx.getSource().getServer().getPlayerManager());
					ctx.getSource().sendFeedback(() -> Text.literal("Resent command tree to client."), true);
					return 1;
				}));
			}
		});
	}

	private static LiteralArgumentBuilder<ServerCommandSource> addNode(String name, InfoNode node) {
		var command = literal(name).executes(ctx -> {
			ctx.getSource().sendFeedback(node::message, false);
			return 1;
		});
		node.subCommands().forEach((commandName, subNode) -> {
			command.then(addNode(commandName, subNode));
		});
		return command;
	}

}

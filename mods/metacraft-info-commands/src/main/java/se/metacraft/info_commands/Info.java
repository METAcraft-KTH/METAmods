package se.metacraft.info_commands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.info_commands.command_action.CommandActionRegistry;
import se.metacraft.info_commands.command_action.CommandCommandAction;
import se.metacraft.info_commands.command_action.MessageCommandAction;
import se.metacraft.lib.METAcraftLib;

import java.util.HashMap;
import java.util.Optional;

public class Info implements ModInitializer {
	private static final ConfigContainer<InfoConfig> config = ConfigContainer.Builder.create(
			InfoConfig.CODEC, () -> {
				var config = new InfoConfig();
				var sub = new HashMap<String, CommandActionNode>();
				config.commands().put("example1", new CommandActionNode(new MessageCommandAction(Component.literal("Test")), sub));
				sub.put("example3", new CommandActionNode(new MessageCommandAction(Component.literal("Look"))));
				var sub2 = new HashMap<String, CommandActionNode>();
				sub2.put("example5", new CommandActionNode(new MessageCommandAction(Component.literal("Without limits"))));
				sub.put("example4", new CommandActionNode(new MessageCommandAction(Component.literal("It's ").append(Component.literal("recursive."))), sub2));
				var otherSub = new CommandActionNode(
					new MessageCommandAction(Component.literal("And it supports JSON text! ").setStyle(
						Style.EMPTY.withBold(true).withItalic(true).withColor(0xccffff)
					).append(Component.literal("Cool right?").setStyle(Style.EMPTY.withObfuscated(true))))
				);
				otherSub.subCommands().put(
					"example6",
					new CommandActionNode(
						new CommandCommandAction(
							"say AND NOW COMMANDS!!!!!",
							Optional.of(PermissionLevel.GAMEMASTERS)
						)
					)
				);
				config.commands().put("example2", otherSub);
				config.infoMessages().add(new InfoMessage("example", Component.literal("Did you know? You can use /example1 to see information!")));
				return config;
			}
	).reloadBeforeServer().build("metacraft-info-commands");

	public static final Logger LOGGER = LogManager.getLogger("METAcraft-info-commands");

	private final InfoMessages infoMessages = new InfoMessages();

	public static Identifier getId(String id) {
		return METAcraftLib.getID(id);
	}

	@Override
	public void onInitialize() {
		CommandActionRegistry.init();
		Commands.init();

		ServerTickEvents.END_SERVER_TICK.register(server -> this.infoMessages.tick(server, getConfig()));
	}

	/**
	 * Returns the config.
	 * DO NOT CACHE THIS IN VARIABLES FOR LONGER PERIODS OF TIME!
	 * @return The config.
	 */
	public static InfoConfig getConfig() {
		return config.get();
	}
}

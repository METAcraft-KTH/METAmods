package nu.metacraft.info_commands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.config.container.ConfigContainer;

import java.nio.file.Path;
import java.util.HashMap;

public class Info implements ModInitializer {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("metacraft-info-commands.json");
	private static final ConfigContainer<InfoConfig> config = ConfigContainer.Builder.create(
			InfoConfig.CODEC, () -> {
				var config = new InfoConfig();
				var sub = new HashMap<String, InfoNode>();
				config.commands().put("example1", new InfoNode(Component.literal("Test"), sub));
				sub.put("example3", new InfoNode(Component.literal("Look")));
				var sub2 = new HashMap<String, InfoNode>();
				sub2.put("example5", new InfoNode(Component.literal("Without limits")));
				sub.put("example4", new InfoNode(Component.literal("It's ").append(Component.literal("recursive.")), sub2));
				config.commands().put("example2", new InfoNode(
						Component.literal("And it supports JSON text! ").setStyle(
								Style.EMPTY.withBold(true).withItalic(true).withColor(0xccffff)
						).append(Component.literal("Cool right?").setStyle(Style.EMPTY.withObfuscated(true)))
				));
				config.infoMessages().add(new InfoMessage("example", Component.literal("Did you know? You can use /example1 to see information!")));
				return config;
			}
	).reloadBeforeServer().build(configPath);

	public static final Logger LOGGER = LogManager.getLogger("METAcraft-info-commands");

	private final InfoMessages infoMessages = new InfoMessages();

	@Override
	public void onInitialize() {
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

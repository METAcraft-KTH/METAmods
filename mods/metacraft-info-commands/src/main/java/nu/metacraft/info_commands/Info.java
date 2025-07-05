package nu.metacraft.info_commands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
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
				config.getCommands().put("example1", new InfoNode(Text.literal("Test"), sub));
				sub.put("example3", new InfoNode(Text.literal("Look")));
				var sub2 = new HashMap<String, InfoNode>();
				sub2.put("example5", new InfoNode(Text.literal("Without limits")));
				sub.put("example4", new InfoNode(Text.literal("It's ").append(Text.literal("recursive.")), sub2));
				config.getCommands().put("example2", new InfoNode(
						Text.literal("And it supports JSON text! ").setStyle(
								Style.EMPTY.withBold(true).withItalic(true).withColor(0xccffff)
						).append(Text.literal("Cool right?").setStyle(Style.EMPTY.withObfuscated(true)))
				));
				return config;
			}
	).reloadBeforeServer().build(configPath);

	public static final Logger LOGGER = LogManager.getLogger("METAcraft-info-commands");
	@Override
	public void onInitialize() {
		Commands.init();
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

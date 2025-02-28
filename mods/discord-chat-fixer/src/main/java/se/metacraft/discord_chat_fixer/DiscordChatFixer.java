package se.metacraft.discord_chat_fixer;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;

public class DiscordChatFixer implements ModInitializer {

	public static final String MODID = "discord-chat-fixer";

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		if (DiscordMixinConfig.isLoaded()) {
			LOGGER.info("{} is loaded, applying fixes!", IsLoaded.DISCORD_MC_CHAT.modID());
		}
		if (IsLoaded.DISCORD_MC_CHAT.isLoaded() && !DiscordMixinConfig.isLoaded()) {
			LOGGER.error("{} was installed, but not detected properly!", IsLoaded.DISCORD_MC_CHAT.modID());
		}
	}

	public static Identifier getID(String name) {
		return Identifier.of(MODID, name);
	}
}

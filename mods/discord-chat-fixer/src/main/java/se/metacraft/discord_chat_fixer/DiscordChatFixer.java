package se.metacraft.discord_chat_fixer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.metacraft.discord_chat_fixer.compat.CoreCompat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Optional;

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

	public static Optional<String> getAvatarURL(ServerCommandSource source) {
		return getProfileFromSource(source).flatMap(DiscordChatFixer::getAvatarURL);
	}

	public static Optional<GameProfile> getProfileFromSource(ServerCommandSource source) {
		if (IsLoaded.CORE.isLoaded()) {
			if (CoreCompat.isMETAcraftPlayer(source.getEntity())) {
				return Optional.of(CoreCompat.getFromMETAcraftPlayer(source.getEntity()));
			}
		}
		return Optional.empty();
	}

	public static Optional<String> getAvatarURL(GameProfile profile) {
		String hash = "null";
		var api = DiscordConfigAccessor.getAvatarAPI();
		if (api.contains("{player_textures}")) {
			try {
				String textures = profile.getProperties().get("textures").iterator().next().value();

				JsonObject json = new Gson().fromJson(new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8), JsonObject.class);
				String url = json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();

				hash = url.replace("http://textures.minecraft.net/texture/", "");
			} catch (NoSuchElementException ignored) {
			}
		}

		if (hash.equals("null")) {
			return Optional.empty();
		}

		return Optional.of(api.replace("{player_textures}", hash));
	}

	public static Identifier getID(String name) {
		return Identifier.of(MODID, name);
	}
}

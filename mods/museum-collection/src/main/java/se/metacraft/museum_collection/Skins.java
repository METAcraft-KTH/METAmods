package se.metacraft.museum_collection;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * The hash of a player's skin on textures.minecraft.net, which is what the website draws their head from.
 *
 * <p>It comes from the {@code textures} property Mojang signed into the player's profile at login, so it costs
 * no request to anyone. The website then only fetches {@code textures.minecraft.net/texture/<hash>}: a CDN
 * whose files never change (a new skin is a new hash), instead of asking a profile API per player.
 */
public final class Skins {

	private Skins() {}

	/** The player's skin hash, or null for one without a skin texture (offline mode, the default skin). */
	@Nullable
	public static String hash(ServerPlayer player) {
		return hash(player.getGameProfile());
	}

	@Nullable
	public static String hash(GameProfile profile) {
		try {
			var textures = profile.properties().get("textures");
			if (textures.isEmpty()) {
				return null;
			}
			String json = new String(Base64.getDecoder().decode(textures.iterator().next().value()), StandardCharsets.UTF_8);
			var skin = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
			if (skin == null) {
				return null;
			}
			String url = skin.get("url").getAsString();
			String hash = url.substring(url.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
			return hash.matches("[0-9a-f]{1,64}") ? hash : null;
		} catch (RuntimeException e) {
			// Not a property Mojang wrote: no skin is better than a broken one.
			return null;
		}
	}
}

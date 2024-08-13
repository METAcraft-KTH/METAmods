package se.datasektionen.mc.metacraft_lib.util.helper;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import se.datasektionen.mc.metacraft_lib.METAcraftData;

import java.util.Optional;
import java.util.UUID;

public class GameProfileHelper {

	/**
	 * Fetches the game profile for the given UUID.
	 * This provided game profile will contain skin data and such if the given player is online.
	 * However, if the player is offline, the game profile will be fetched from the user cache instead.
	 * @param uuid THe UUID of the player.
	 * @param server The server to work with.
	 * @return A game profile, or empty if not found.
	 */
	public static Optional<GameProfile> getForUUID(UUID uuid, MinecraftServer server) {
		var player = server.getPlayerManager().getPlayer(uuid);
		if (player != null) {
			return Optional.of(player.getGameProfile());
		} else {
			return server.getUserCache().getByUuid(uuid);
		}
	}

	/**
	 * Returns the name for the given player, taking their custom name into account.
	 * @param profile The game profile of the player.
	 * @param server The server in question.
	 * @return The name to display.
	 */
	public static String getNameFromProfile(GameProfile profile, MinecraftServer server) {
		return METAcraftData.getInstance(server).getName(profile);
	}

}

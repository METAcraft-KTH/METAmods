package se.datasektionen.mc.metacraft_lib.util.helper;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;

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

}

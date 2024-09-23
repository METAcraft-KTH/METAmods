package se.datasektionen.mc.metacraft_moderation;

import com.mojang.authlib.GameProfile;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_moderation.moderator_mode.ModerationModeState;

import java.util.Optional;
import java.util.UUID;

public class PlayerModerationState {

	public static boolean setModeratorMode(ServerPlayerEntity player, String mode) {
		if (!canEnterModerationMode(player, mode)) {
			return false;
		}
		var data = ModerationData.getInstance(player.getServer());
		return data.getDefinition(mode).map(actualMode -> {
			var state = new ModerationModeState(actualMode);
			((ModerationPlayerData) player).METAcraft_Moderation$setModerationMode(state);
			return true;
		}).orElse(false);
	}

	public static boolean canEnterModerationMode(ServerPlayerEntity player, String mode) {
		return Permissions.check(player, "metacraft.mod." + mode, 3);
	}

	public static void removeModeratorMode(ServerPlayerEntity player) {
		((ModerationPlayerData) player).METAcraft_Moderation$setModerationMode(null);
	}

	public static Optional<ModerationModeState> getPlayerState(ServerPlayerEntity player) {
		return ((ModerationPlayerData) player).METAcraft_Moderation$getModerationMode();
	}

	public static Optional<ModerationModeState> getPlayerState(MinecraftServer server, UUID playerID) {
		var player = server.getPlayerManager().getPlayer(playerID);
		if (player == null) {
			player = new FakePlayer(
					server.getOverworld(),
					Optional.ofNullable(server.getUserCache()).flatMap(cache -> cache.getByUuid(playerID)).orElse(
							new GameProfile(playerID, "missingno")
					)
			) {};
			server.getPlayerManager().loadPlayerData(player);
		}
		return getPlayerState(player);
	}

}

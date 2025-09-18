package nu.metacraft.moderation;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.moderation.moderator_mode.ModerationModeState;

import java.util.Optional;

public class PlayerModerationState {

	public static boolean setModeratorMode(ServerPlayerEntity player, String mode) {
		if (!canEnterModerationMode(player, mode)) {
			return false;
		}
		var data = ModerationData.getInstance(player.getEntityWorld().getServer());
		return data.getDefinition(mode).map(actualMode -> {
			var state = new ModerationModeState(actualMode);
			((ModerationPlayerData) player).METAcraft_Moderation$setModerationMode(state);
			return true;
		}).orElse(false);
	}

	public static boolean canEnterModerationMode(ServerPlayerEntity player, String mode) {
		return Permissions.check(player.getCommandSource(), "metacraft.mod." + mode, 3);
	}

	public static void removeModeratorMode(ServerPlayerEntity player) {
		((ModerationPlayerData) player).METAcraft_Moderation$setModerationMode(null);
	}

	public static Optional<ModerationModeState> getPlayerState(ServerPlayerEntity player) {
		return ((ModerationPlayerData) player).METAcraft_Moderation$getModerationMode();
	}

}

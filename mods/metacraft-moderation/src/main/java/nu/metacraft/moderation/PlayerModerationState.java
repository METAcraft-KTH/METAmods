package nu.metacraft.moderation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.moderation.moderator_mode.ModerationModeState;

import java.util.Optional;

public class PlayerModerationState {

	public static boolean setModeratorMode(ServerPlayer player, String mode) {
		if (!canEnterModerationMode(player, mode)) {
			return false;
		}
		var data = ModerationData.getInstance(player.level().getServer());
		return data.getDefinition(mode).map(actualMode -> {
			var state = new ModerationModeState(actualMode);
			((ModerationPlayerData) player).METAcraft_Moderation$setModerationMode(state);
			return true;
		}).orElse(false);
	}

	public static boolean canEnterModerationMode(ServerPlayer player, String mode) {
		return player.createCommandSourceStack().checkPermission(METAcraftLib.getID("mod." + mode), PermissionLevel.ADMINS);
	}

	public static void removeModeratorMode(ServerPlayer player) {
		((ModerationPlayerData) player).METAcraft_Moderation$setModerationMode(null);
	}

	public static Optional<ModerationModeState> getPlayerState(ServerPlayer player) {
		return ((ModerationPlayerData) player).METAcraft_Moderation$getModerationMode();
	}

}

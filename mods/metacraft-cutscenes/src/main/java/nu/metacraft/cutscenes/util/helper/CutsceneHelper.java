package nu.metacraft.cutscenes.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.cutscenes.extension.ServerPlayerEntityExtensions;
import nu.metacraft.cutscenes.cutscene.Cutscene;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;

import java.util.Optional;

public class CutsceneHelper {

	public static boolean isInPlayerSpecificCutscene(ServerPlayerEntity player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_cutscenes$hasCutscene();
	}

	public static boolean isInMultiplayerCutscene(ServerPlayerEntity player) {
		return MultiplayerCutsceneManager.getInstance(player.getServer()).isInCutscene(player);
	}

	public static boolean isInCutscene(ServerPlayerEntity player) {
		return isInMultiplayerCutscene(player) || isInPlayerSpecificCutscene(player);
	}

	public static Optional<CutsceneInstance> getCutscene(ServerPlayerEntity player) {
		if (isInMultiplayerCutscene(player)) {
			return MultiplayerCutsceneManager.getInstance(player.getServer()).getCutsceneFromPlayer(player);
		} else if (isInPlayerSpecificCutscene(player)) {
			return ((ServerPlayerEntityExtensions) player).metacraft_cutscenes$getCutscene();
		} else {
			return Optional.empty();
		}
	}

	public static void playPlayerSpecificCutscene(ServerPlayerEntity player, Cutscene cutscene) {
		((ServerPlayerEntityExtensions) player).metacraft_cutscenes$setCutscene(new CutsceneInstance(cutscene, player.getWorld()));
	}

	public static void stopPlayerSpecificCutscene(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_cutscenes$setCutscene(null);
	}

	public static void forceOutOfCutscene(ServerPlayerEntity player) {
		if (isInPlayerSpecificCutscene(player)) {
			stopPlayerSpecificCutscene(player);
		}
		if (isInMultiplayerCutscene(player)) {
			MultiplayerCutsceneManager.getInstance(player.getServer()).leaveCutscene(player);
		}
	}

}

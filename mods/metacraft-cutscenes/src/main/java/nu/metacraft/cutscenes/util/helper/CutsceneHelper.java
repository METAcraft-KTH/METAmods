package nu.metacraft.cutscenes.util.helper;

import nu.metacraft.cutscenes.extension.ServerPlayerEntityExtensions;
import nu.metacraft.cutscenes.cutscene.Cutscene;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

public class CutsceneHelper {

	public static boolean isInPlayerSpecificCutscene(ServerPlayer player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_cutscenes$hasCutscene();
	}

	public static boolean isInMultiplayerCutscene(ServerPlayer player) {
		return MultiplayerCutsceneManager.getInstance(player.level().getServer()).isInCutscene(player);
	}

	public static boolean isInCutscene(ServerPlayer player) {
		return isInMultiplayerCutscene(player) || isInPlayerSpecificCutscene(player);
	}

	public static Optional<CutsceneInstance> getCutscene(ServerPlayer player) {
		if (isInMultiplayerCutscene(player)) {
			return MultiplayerCutsceneManager.getInstance(player.level().getServer()).getCutsceneFromPlayer(player);
		} else if (isInPlayerSpecificCutscene(player)) {
			return ((ServerPlayerEntityExtensions) player).metacraft_cutscenes$getCutscene();
		} else {
			return Optional.empty();
		}
	}

	public static void playPlayerSpecificCutscene(ServerPlayer player, Cutscene cutscene) {
		((ServerPlayerEntityExtensions) player).metacraft_cutscenes$setCutscene(new CutsceneInstance(cutscene, player.level()));
	}

	public static void stopPlayerSpecificCutscene(ServerPlayer player) {
		((ServerPlayerEntityExtensions) player).metacraft_cutscenes$setCutscene(null);
	}

	public static void forceOutOfCutscene(ServerPlayer player) {
		if (isInPlayerSpecificCutscene(player)) {
			stopPlayerSpecificCutscene(player);
		}
		if (isInMultiplayerCutscene(player)) {
			MultiplayerCutsceneManager.getInstance(player.level().getServer()).leaveCutscene(player);
		}
	}

}

package se.datasektionen.mc.cutscenes.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.cutscenes.extension.ServerPlayerEntityExtensions;
import se.datasektionen.mc.cutscenes.cutscene.Cutscene;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;

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

	public static void playPlayerSpecificCutscene(ServerPlayerEntity player, Cutscene cutscene) {
		((ServerPlayerEntityExtensions) player).metacraft_cutscenes$setCutscene(new CutsceneInstance(cutscene, player.getServerWorld()));
	}

	public static void stopPlayerSpecificCutscene(ServerPlayerEntity player) {
		((ServerPlayerEntityExtensions) player).metacraft_cutscenes$setCutscene(null);
	}

}

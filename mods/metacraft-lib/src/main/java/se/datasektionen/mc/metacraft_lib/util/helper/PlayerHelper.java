package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;

@SuppressWarnings("unused")
public class PlayerHelper {

	public static boolean shouldShowInGUI(ServerPlayerEntity player) {
		return ((ServerPlayerEntityExtensions) player).METAcraft_Moderation$showInGUI() && !VanishHelper.isVanished(player);
	}

}

package nu.metacraft.lib.util.helper;

import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;

@SuppressWarnings("unused")
public class PlayerHelper {

	public static boolean shouldShowInGUI(ServerPlayerEntity player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_lib$showInGUI() && !VanishHelper.isVanished(player);
	}


}

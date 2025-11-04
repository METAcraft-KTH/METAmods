package nu.metacraft.lib.util.helper;

import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;

@SuppressWarnings("unused")
public class PlayerHelper {

	public static boolean shouldShowInGUI(ServerPlayer player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_lib$showInGUI() && !VanishHelper.isVanished(player);
	}


}

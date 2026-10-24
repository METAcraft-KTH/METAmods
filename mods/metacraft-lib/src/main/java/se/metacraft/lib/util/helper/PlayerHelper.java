package se.metacraft.lib.util.helper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;
import se.metacraft.lib.callbacks.GetScoreboardCallback;
import se.metacraft.lib.extensions.ServerPlayerExtensions;

@SuppressWarnings("unused")
public class PlayerHelper {

	public static boolean shouldShowInGUI(ServerPlayer player) {
		return ((ServerPlayerExtensions) player).metacraft_lib$showInGUI() && !VanishHelper.isVanished(player);
	}

	public static Scoreboard getCurrentlyVisibleScoreboard(ServerPlayer player) {
		return GetScoreboardCallback.EVENT.invoker().getScoreboard(player).orElseGet(() -> player.level().getScoreboard());
	}


}

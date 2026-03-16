package nu.metacraft.player_specific_scoreboards;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.player_specific_scoreboards.util.PlayerScoreboard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayerScoreboards implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final String MODID = "metacraft-player-specific-scoreboards";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		Commands.init();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}

	public static void setPlayerScoreboard(ServerPlayer player, PlayerScoreboard scoreboard) {
		((PlayerScoreboardExtension) player).metacraft$setScoreboard(scoreboard);
	}

	public static void clearPlayerScoreboard(ServerPlayer player) {
		setPlayerScoreboard(player, null);
	}
}

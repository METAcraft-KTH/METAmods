package nu.metacraft.player_specific_scoreboards;

import nu.metacraft.player_specific_scoreboards.util.PlayerScoreboard;

public interface PlayerScoreboardExtension {

	void metacraft$setScoreboard(PlayerScoreboard playerScoreboard);
	void metacraft$updateScoreboard();


	PlayerScoreboard metacraft$getPrevScoreboard();

	PlayerScoreboard metacraft$getCurrentScoreboard();

	boolean metacraft$getExistsClientside();

}

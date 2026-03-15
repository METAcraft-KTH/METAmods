package nu.metacraft.player_specific_scoreboards;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import nu.metacraft.player_specific_scoreboards.commands.SetPlayerScoreboardCommand;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandBuildContext, commandSelection) -> {
			SetPlayerScoreboardCommand.register(commandDispatcher);
		}));
	}

}

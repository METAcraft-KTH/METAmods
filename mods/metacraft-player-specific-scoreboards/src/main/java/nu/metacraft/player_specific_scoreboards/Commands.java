package nu.metacraft.player_specific_scoreboards;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import nu.metacraft.player_specific_scoreboards.commands.SetPlayerSidebarCommand;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandBuildContext, commandSelection) -> {
			SetPlayerSidebarCommand.register(commandDispatcher);
		}));
	}

}

package nu.metacraft.bosses;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import nu.metacraft.bosses.commands.DoubleTeamCommand;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DoubleTeamCommand.register(dispatcher, registryAccess);
		});
	}

}

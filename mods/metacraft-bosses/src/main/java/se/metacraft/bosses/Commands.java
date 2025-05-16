package se.metacraft.bosses;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import se.metacraft.bosses.commands.DoubleTeamCommand;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DoubleTeamCommand.register(dispatcher, registryAccess);
		});
	}

}

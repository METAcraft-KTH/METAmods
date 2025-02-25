package se.datasektionen.mc.metacraft_season_4;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import se.datasektionen.mc.metacraft_season_4.commands.CampusLodestoneCommand;
import se.datasektionen.mc.metacraft_season_4.commands.DoubleTeamCommand;
import se.datasektionen.mc.metacraft_season_4.commands.EndBossCommand;
import se.datasektionen.mc.metacraft_season_4.commands.EndPortalActivationOverride;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			CampusLodestoneCommand.register(dispatcher);
			DoubleTeamCommand.register(dispatcher, registryAccess);
			EndPortalActivationOverride.register(dispatcher, registryAccess);
			EndBossCommand.register(dispatcher, registryAccess);
		});
	}

}

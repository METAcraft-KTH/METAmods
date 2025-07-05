package nu.metacraft.season_4;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import nu.metacraft.season_4.commands.CampusLodestoneCommand;
import nu.metacraft.season_4.commands.EndBossCommand;
import nu.metacraft.season_4.commands.EndPortalActivationOverride;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			CampusLodestoneCommand.register(dispatcher);
			EndPortalActivationOverride.register(dispatcher, registryAccess);
			EndBossCommand.register(dispatcher, registryAccess);
		});
	}

}

package nu.metacraft.minigame_util;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import nu.metacraft.minigame_util.commands.ForAllBlockEntities;
import nu.metacraft.minigame_util.commands.SetBlockListCommand;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, env) -> {
			SetBlockListCommand.register(dispatcher, registryAccess);
			ForAllBlockEntities.register(dispatcher, registryAccess);
		}));
	}

}

package se.datasektionen.mc.metacraft_lib;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import se.datasektionen.mc.metacraft_lib.commands.*;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ExplorerMapCommand.register(dispatcher, registryAccess);
			HardcoreSwitcherCommand.register(dispatcher, registryAccess);
			CustomNameCommand.register(dispatcher, registryAccess);
			PlaySoundFromEntity.register(dispatcher, registryAccess);
			PlayerDataCommand.register(dispatcher, registryAccess);
		});
	}

}

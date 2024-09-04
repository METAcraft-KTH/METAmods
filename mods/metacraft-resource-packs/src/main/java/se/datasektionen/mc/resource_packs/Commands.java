package se.datasektionen.mc.resource_packs;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ResourcePackCommand.register(dispatcher, registryAccess);
		});
	}

}

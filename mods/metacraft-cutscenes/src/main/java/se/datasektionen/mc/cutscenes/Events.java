package se.datasektionen.mc.cutscenes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;

public class Events {

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MultiplayerCutsceneManager.getInstance(server).tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MultiplayerCutsceneManager.getInstance(server).onServerShutdown();
		});
	}

}

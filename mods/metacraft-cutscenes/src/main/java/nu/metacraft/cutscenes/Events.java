package nu.metacraft.cutscenes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;

public class Events {

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MultiplayerCutsceneManager.getInstance(server).tick();
		});
	}

}

package se.metacraft.cutscenes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import se.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;
import se.metacraft.cutscenes.util.helper.CutsceneHelper;
import se.metacraft.lib.callbacks.GetScoreboardCallback;

public class Events {

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MultiplayerCutsceneManager.getInstance(server).tick();
		});
		GetScoreboardCallback.EVENT.register(
			player -> CutsceneHelper.getCutscene(player).map(
				scene -> scene.getCutsceneWorld().getScoreboard()
			)
		);
	}

}

package nu.metacraft.cutscenes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import nu.metacraft.cutscenes.cutscene.MultiplayerCutsceneManager;
import nu.metacraft.cutscenes.util.helper.CutsceneHelper;
import nu.metacraft.lib.callbacks.GetScoreboardCallback;

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

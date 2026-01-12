package nu.metacraft.pause;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class PauseEvents {

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PauseData.getInstance(server).updatePauseState(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PauseData.getInstance(server).killTimer();
		});

		ServerPlayerEvents.JOIN.register(player ->  {
			PauseData.getInstance(player.level().getServer()).onPlayerJoin(player);
		});
	}

}

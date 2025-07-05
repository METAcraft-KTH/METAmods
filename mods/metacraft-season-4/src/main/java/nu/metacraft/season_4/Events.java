package nu.metacraft.season_4;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import nu.metacraft.season_4.end.EndBossPlayerState;

public class Events {

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			EndBossPlayerState.getInstance(world).ifPresent(inst -> inst.tick(world));
		});
	}

}

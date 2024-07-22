package se.datasektionen.mc.simplecustomfeatures;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Events {

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ObjectCache.getInstance(server).onLoad();
		});
	}

}

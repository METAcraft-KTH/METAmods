package se.metacraft.museum_collection;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * The Museum Collection Competition: three teams collect items. A player registers an item by right-clicking a collector
 * villager with it (the item is kept); each player can register each item once, and a team's score is the
 * number of different items its players have registered. Everything is stored in MySQL, where the website
 * reads it. See README.md in this module.
 */
public final class MuseumCollection implements ModInitializer {

	public static final String MODID = "museum-collection";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Nullable
	private static EventService service;

	@Override
	public void onInitialize() {
		MuseumConfig.get();
		Collector.init();
		TeamSelect.init();
		MuseumCommand.init();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> service = new EventService(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (service != null) {
				// The last submissions are written before the server is gone.
				service.stop(true);
				service = null;
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (service != null) {
				service.tick();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (service != null) {
				service.onJoin(handler.getPlayer());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (service != null) {
				service.onLeave(handler.getPlayer());
			}
		});
	}

	/** The event on the running server; null while no server is running. */
	@Nullable
	public static EventService service() {
		return service;
	}

	public static Identifier getID(String path) {
		return Identifier.fromNamespaceAndPath(MODID, path);
	}
}

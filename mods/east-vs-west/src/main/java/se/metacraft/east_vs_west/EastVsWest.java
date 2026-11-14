package se.metacraft.east_vs_west;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * East vs West vs Rest: three teams collect items. A player registers an item by right-clicking a collector
 * villager with it (the item is kept); each player can register each item once, and a team's score is the
 * number of different items its players have registered. Everything is stored in MySQL, where the website
 * reads it. See README.md in this module.
 */
public final class EastVsWest implements ModInitializer {

	public static final String MODID = "east-vs-west";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Nullable
	private static EventService service;
	private static final Cosmetics COSMETICS = new Cosmetics();

	@Override
	public void onInitialize() {
		EvwConfig.get();
		Collector.init();
		TeamSelect.init();
		EvwCommand.init();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> service = new EventService(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			COSMETICS.clear();
			if (service != null) {
				// The last submissions are written before the server is gone.
				service.stop(true);
				service = null;
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (service != null) {
				service.tick();
				COSMETICS.tick(server, service);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (service != null) {
				service.onJoin(handler.getPlayer());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			COSMETICS.onLeave(handler.getPlayer());
			if (service != null) {
				service.onLeave(handler.getPlayer());
			}
		});

		// The legendary hat's model (the lid of an ender chest).
		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
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

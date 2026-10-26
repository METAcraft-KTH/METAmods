package se.metacraft.traders;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Traders implements ModInitializer {

	public static final String MODID = "traders";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		TradersConfig.get();
		TraderModels.init();
		TraderEntities.init();
		TradersCommand.init();
		// Every mod's items are registered by now: work out the trades on offer, so one with a missing item is logged at startup.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> TradersConfig.tradesOnOffer());

		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String path) {
		return Identifier.fromNamespaceAndPath(MODID, path);
	}
}

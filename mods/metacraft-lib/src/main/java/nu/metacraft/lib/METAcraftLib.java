package nu.metacraft.lib;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import nu.metacraft.lib.scheduler.METAcraftScheduleTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.condition.METAcraftConditions;
import nu.metacraft.lib.time_getter.RegularTimeGetterRegistry;

public class METAcraftLib implements ModInitializer {

	public static final String NAMESPACE = "metacraft";
	public static final Logger LOGGER = LogManager.getLogger("metacraft-lib");

	@Override
	public void onInitialize() {
		METAcraftTickets.init();
		RegularTimeGetterRegistry.init();
		METAcraftConditions.init();
		Commands.init();
		Recipes.init();
		METAcraftScheduleTypes.init();
	}

	public static ResourceLocation getID(String id) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
	}
}

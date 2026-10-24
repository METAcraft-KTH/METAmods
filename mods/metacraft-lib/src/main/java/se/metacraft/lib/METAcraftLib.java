package se.metacraft.lib;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import se.metacraft.lib.scheduler.METAcraftScheduleTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.lib.condition.METAcraftConditions;
import se.metacraft.lib.custom_message.CustomMessageRegistry;
import se.metacraft.lib.time_getter.RegularTimeGetterRegistry;

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
		// registered up front: left to the first click on a custom action, it lands after the registries froze
		CustomMessageRegistry.init();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}

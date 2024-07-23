package se.datasektionen.mc.metacraft_lib;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.condition.METAcraftConditions;
import se.datasektionen.mc.metacraft_lib.time_getter.RegularTimeGetterRegistry;
import se.datasektionen.mc.metacraft_lib.util.impl.TaskSchedulerImpl;

public class METAcraftLib implements ModInitializer {

	public static final String NAMESPACE = "metacraft";
	public static final Logger LOGGER = LogManager.getLogger("metacraft-lib");

	@Override
	public void onInitialize() {
		RegularTimeGetterRegistry.init();
		METAcraftConditions.init();
		Commands.init();
		Recipes.init();
		TaskSchedulerImpl.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}

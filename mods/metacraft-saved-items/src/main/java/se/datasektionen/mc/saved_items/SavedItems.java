package se.datasektionen.mc.saved_items;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.saved_items.loot_container.Containers;

public class SavedItems implements ModInitializer {

	public static final String NAMESPACE = "metacraft_saved_items";
	public static final String MODID = "metacraft-saved-items";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}

	@Override
	public void onInitialize() {
		if (IsLoaded.METACRAFT_LOOT_CONTAINERS.isLoaded()) {
			Containers.init();
		}
		Commands.init();
		SavedItemsConfig.getConfig(); //Initialises Config.
	}

}

package se.datasektionen.mc.saved_items;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
		if (FabricLoader.getInstance().isModLoaded("metacraft-loot-containers")) {
			Containers.init();
		}
		Commands.init();
		SavedItemsConfig.getConfig(); //Initialises Config.
	}

}

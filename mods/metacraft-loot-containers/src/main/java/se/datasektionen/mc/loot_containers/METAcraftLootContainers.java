package se.datasektionen.mc.loot_containers;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.loot_containers.containers.LootContainerRegistry;

public class METAcraftLootContainers implements ModInitializer {

	public static final String NAMESPACE = "metacraft_loot_containers";
	public static final String MODID = "metacraft-loot-containers";
	public static final Logger LOGGER = LogManager.getLogger("metacraft-loot-containers");

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}

	@Override
	public void onInitialize() {
		LootContainerRegistry.init();
		Commands.init();
		Events.init();
		LootContainerConfig.getConfig(); //Initialises Config.
	}

}

package nu.metacraft.loot_containers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.loot_containers.containers.LootContainerRegistry;

public class METAcraftLootContainers implements ModInitializer {

	public static final String NAMESPACE = "metacraft_loot_containers";
	public static final String MODID = "metacraft-loot-containers";
	public static final Logger LOGGER = LogManager.getLogger("metacraft-loot-containers");

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}

	@Override
	public void onInitialize() {
		LootContainerRegistry.init();
		Commands.init();
		Events.init();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LootContainerConfig.getConfig(); //Initialises Config.
		});
	}

}

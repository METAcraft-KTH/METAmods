package nu.metacraft.relay;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.relay.blocks.RelayBlockEntities;
import nu.metacraft.relay.blocks.RelayBlocks;
import nu.metacraft.relay.items.RelayComponents;
import nu.metacraft.relay.items.RelayItems;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.METAcraftLib;

public class Relay implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final Logger LOGGER = LogManager.getLogger("metacraft-relay");
	public static final String MODID = "metacraft-relay";

	@Override
	public void onInitialize() {
		RelayBlocks.init();
		RelayBlockEntities.init();
		RelayComponents.init();
		RelayItems.init();
		Events.init();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}

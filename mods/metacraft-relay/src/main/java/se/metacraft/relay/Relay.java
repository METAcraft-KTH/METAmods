package se.metacraft.relay;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import se.metacraft.relay.blocks.RelayBlockEntities;
import se.metacraft.relay.blocks.RelayBlocks;
import se.metacraft.relay.items.RelayComponents;
import se.metacraft.relay.items.RelayItems;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.lib.METAcraftLib;

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

		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}

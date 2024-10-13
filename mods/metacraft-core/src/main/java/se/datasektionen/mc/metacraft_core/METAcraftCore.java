package se.datasektionen.mc.metacraft_core;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.compat.CompatInit;
import se.datasektionen.mc.metacraft_core.entity.METAcraftEntities;
import se.datasektionen.mc.metacraft_core.item.METAcraftItems;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;

public class METAcraftCore implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final Logger LOGGER = LogManager.getLogger("metacraft-core");

	@Override
	public void onInitialize() {
		METAcraftComponents.init();
		METAcraftBlocks.init();
		METAcraftEntities.init();
		METAcraftItems.init();
		Commands.init();
		Events.init();
		CompatInit.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}

package se.datasektionen.mc.metacraft_season_4;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_season_4.block.Season4Blocks;
import se.datasektionen.mc.metacraft_season_4.compat.CompatInitS4;
import se.datasektionen.mc.metacraft_season_4.item.Season4Items;

public class Season4 implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final Logger LOGGER = LogManager.getLogger("metacraft-season-4");

	@Override
	public void onInitialize() {
		Season4Blocks.init();
		Season4Items.init();
		Commands.init();
		CompatInitS4.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}

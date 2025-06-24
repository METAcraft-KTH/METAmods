package se.datasektionen.mc.metacraft_season_4;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_season_4.block.Season4Blocks;
import se.datasektionen.mc.metacraft_season_4.boss.Season4Attacks;
import se.datasektionen.mc.metacraft_season_4.compat.CompatInitS4;
import se.datasektionen.mc.metacraft_season_4.entity.Season4Entities;
import se.datasektionen.mc.metacraft_season_4.item.Season4Items;
import se.datasektionen.mc.metacraft_season_4.status_effects.Season4StatusEffects;

public class Season4 implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final Logger LOGGER = LogManager.getLogger("metacraft-season-4");

	@Override
	public void onInitialize() {
		Season4StatusEffects.init();
		Season4Blocks.init();
		Season4Items.init();
		Season4Entities.init();
		Season4Attacks.init();
		Commands.init();
		CompatInitS4.init();
		Events.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}
}

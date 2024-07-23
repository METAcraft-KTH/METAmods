package se.datasektionen.mc.metacraft_dungeons;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonsBlockEntities;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonBlocks;

public class METAcraftDungeons implements ModInitializer {

	public static final String NAMESPACE = "metacraft_dungeons";
	public static final String MODID = "metacraft-dungeons";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static Identifier getID(String id) {
		return Identifier.of(NAMESPACE, id);
	}

	@Override
	public void onInitialize() {
		DungeonBlocks.init();
		DungeonsBlockEntities.init();
		Commands.init();
		Events.init();
	}

}

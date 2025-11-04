package nu.metacraft.dungeons;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.dungeons.block.DungeonsBlockEntities;
import nu.metacraft.dungeons.block.DungeonBlocks;
import nu.metacraft.lib.METAcraftLib;

public class METAcraftDungeons implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final String MODID = "metacraft-dungeons";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static ResourceLocation getID(String id) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
	}

	@Override
	public void onInitialize() {
		DungeonTickets.init();
		DungeonBlocks.init();
		DungeonsBlockEntities.init();
		Commands.init();
		Events.init();
	}

}

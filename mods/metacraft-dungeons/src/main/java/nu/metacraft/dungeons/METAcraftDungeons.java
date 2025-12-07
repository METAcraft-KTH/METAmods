package nu.metacraft.dungeons;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.dungeons.environment_attributes.DungeonAttributeTypes;
import nu.metacraft.dungeons.environment_attributes.DungeonAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.dungeons.block.DungeonsBlockEntities;
import nu.metacraft.dungeons.block.DungeonBlocks;
import nu.metacraft.lib.METAcraftLib;

public class METAcraftDungeons implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final String MODID = "metacraft-dungeons";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}

	@Override
	public void onInitialize() {
		DungeonTickets.init();
		DungeonBlocks.init();
		DungeonsBlockEntities.init();
		DungeonAttributeTypes.init();
		DungeonAttributes.init();
		Commands.init();
		Events.init();
	}

}

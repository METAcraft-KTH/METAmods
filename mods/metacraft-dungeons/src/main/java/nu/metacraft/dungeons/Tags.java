package nu.metacraft.dungeons;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class Tags {

	public static final TagKey<Block> DUNGEON_RESET_UNBREAKABLE = TagKey.create(
			Registries.BLOCK, METAcraftDungeons.getID("dungeon_reset_unbreakable")
	);

}

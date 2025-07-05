package nu.metacraft.dungeons;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public class Tags {

	public static final TagKey<Block> DUNGEON_RESET_UNBREAKABLE = TagKey.of(
			RegistryKeys.BLOCK, METAcraftDungeons.getID("dungeon_reset_unbreakable")
	);

}

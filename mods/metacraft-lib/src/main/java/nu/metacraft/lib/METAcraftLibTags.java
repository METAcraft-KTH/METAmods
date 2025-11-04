package nu.metacraft.lib;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class METAcraftLibTags {

	public static class Blocks {
		public static final TagKey<Block> NEVER_TELEPORT_INTO = create("never_teleport_into");

		private static TagKey<Block> create(String key) {
			return TagKey.create(Registries.BLOCK, METAcraftLib.getID(key));
		}
	}

}

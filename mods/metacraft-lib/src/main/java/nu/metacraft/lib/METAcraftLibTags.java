package nu.metacraft.lib;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public class METAcraftLibTags {

	public static class Blocks {
		public static final TagKey<Block> NEVER_TELEPORT_INTO = create("never_teleport_into");

		private static TagKey<Block> create(String key) {
			return TagKey.of(RegistryKeys.BLOCK, METAcraftLib.getID(key));
		}
	}

}

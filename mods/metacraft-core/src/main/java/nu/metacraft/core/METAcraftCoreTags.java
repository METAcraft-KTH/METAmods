package nu.metacraft.core;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public class METAcraftCoreTags {

	public static final TagKey<Block> PORTAL = TagKey.of(
			RegistryKeys.BLOCK, METAcraftCore.getID("portal")
	);

	public static final TagKey<Block> PORTAL_PADDING = TagKey.of(
			RegistryKeys.BLOCK, METAcraftCore.getID("portal_padding")
	);

}

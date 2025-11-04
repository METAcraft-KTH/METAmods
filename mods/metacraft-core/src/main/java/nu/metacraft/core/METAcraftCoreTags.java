package nu.metacraft.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class METAcraftCoreTags {

	public static final TagKey<Block> PORTAL = TagKey.create(
			Registries.BLOCK, METAcraftCore.getID("portal")
	);

	public static final TagKey<Block> PORTAL_PADDING = TagKey.create(
			Registries.BLOCK, METAcraftCore.getID("portal_padding")
	);

}

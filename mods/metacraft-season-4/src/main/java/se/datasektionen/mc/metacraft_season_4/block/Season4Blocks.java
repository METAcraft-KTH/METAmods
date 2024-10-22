package se.datasektionen.mc.metacraft_season_4.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_season_4.block.blocks.CampusLodestoneBlock;

public class Season4Blocks {

	public static final Block CAMPUS_LODESTONE = register("campus_lodestone", new CampusLodestoneBlock(
		AbstractBlock.Settings.create().strength(3.5F).mapColor(MapColor.IRON_GRAY)
			.sounds(BlockSoundGroup.LODESTONE).pistonBehavior(PistonBehavior.BLOCK)
	));

	public static void init() {
		Season4BlockEntities.init();
	}


	private static Block register(String id, Block block) {
		return Registry.register(Registries.BLOCK, METAcraftCore.getID(id), block);
	}
}

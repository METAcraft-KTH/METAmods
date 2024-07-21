package se.datasektionen.mc.metacraft_core.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_core.block.blocks.MusicBlock;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class METAcraftBlocks {

	public static final Block MUSIC_PLAYER = register("music_player", new MusicBlock(
			AbstractBlock.Settings.create().strength(
					-1.0f, 3600000.8f
			).dropsNothing().nonOpaque().allowsSpawning(
					(state, world, pos, type) -> false
			).noBlockBreakParticles().pistonBehavior(PistonBehavior.BLOCK)
	));

	public static void init() {
		METAcraftBlockEntities.init();
	}


	private static Block register(String id, Block block) {
		return Registry.register(Registries.BLOCK, METAcraftCore.getID(id), block);
	}
}

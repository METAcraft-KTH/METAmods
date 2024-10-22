package se.datasektionen.mc.metacraft_core.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_core.block.blocks.*;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class METAcraftBlocks {

	public static final Block PORTAL_CORE = register("portal_core", new PortalCore(
			AbstractBlock.Settings.copy(Blocks.END_GATEWAY).noBlockBreakParticles()
	));
	public static final Block BLACK_HOLE_CORE = register("black_hole_core", new BlackHolePortalCore(
			AbstractBlock.Settings.copy(Blocks.END_GATEWAY).noBlockBreakParticles()
	));

	public static final Block PORTAL_PADDING = register("portal_padding", new PortalPadding(
			AbstractBlock.Settings.copy(Blocks.END_GATEWAY).noBlockBreakParticles()
	));

	public static final Block MUSIC_PLAYER = register("music_player", new MusicBlock(
			AbstractBlock.Settings.create().strength(
					-1.0f, 3600000.8f
			).dropsNothing().nonOpaque().allowsSpawning(
					(state, world, pos, type) -> false
			).noBlockBreakParticles().pistonBehavior(PistonBehavior.BLOCK)
	));

	public static final Block TRAP_SPAWNER = register("trap_spawner", new TrapSpawner(
			AbstractBlock.Settings.create().strength(
					-1.0f, 3600000.8f
			).dropsNothing().nonOpaque().allowsSpawning(
					(state, world, pos, type) -> false
			)
	));

	public static void init() {
		METAcraftBlockEntities.init();
	}


	private static Block register(String id, Block block) {
		return Registry.register(Registries.BLOCK, METAcraftCore.getID(id), block);
	}
}

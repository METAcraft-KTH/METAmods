package se.datasektionen.mc.metacraft_dungeons.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.blocks.DungeonEntrance;

public class DungeonBlocks {

	public static final Block DUNGEON_ENTRANCE = register("dungeon_entrance_core", new DungeonEntrance(
			AbstractBlock.Settings.copy(Blocks.END_GATEWAY).noBlockBreakParticles()
	));

	public static void init() {

	}

	private static Block register(String id, Block block) {
		return Registry.register(Registries.BLOCK, METAcraftDungeons.getID(id), block);
	}

}

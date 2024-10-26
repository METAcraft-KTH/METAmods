package se.datasektionen.mc.metacraft_season_4.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.datasektionen.mc.metacraft_season_4.block.blocks.CampusLodestoneBlock;

import java.util.function.Function;

public class Season4Blocks {

	public static final Block CAMPUS_LODESTONE = register(
			"campus_lodestone", CampusLodestoneBlock::new,
			AbstractBlock.Settings.create().strength(3.5F).mapColor(MapColor.IRON_GRAY)
			.sounds(BlockSoundGroup.LODESTONE).pistonBehavior(PistonBehavior.BLOCK)
	);

	public static void init() {
		Season4BlockEntities.init();
	}


	private static Block register(String id, Function<AbstractBlock.Settings, Block> block, AbstractBlock.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.BLOCK, Season4.getID(id));
		return Registry.register(Registries.BLOCK, key, block.apply(settings.registryKey(key)));
	}
}

package nu.metacraft.dungeons.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import nu.metacraft.dungeons.METAcraftDungeons;

import java.util.function.Function;

public class DungeonBlocks {

	public static void init() {

	}

	private static Block register(
			String id, Function<Block.Settings, Block> block,
			AbstractBlock.Settings settings
	) {
		var key = RegistryKey.of(RegistryKeys.BLOCK, METAcraftDungeons.getID(id));
		return Registry.register(Registries.BLOCK, key, block.apply(settings.registryKey(key)));
	}

}

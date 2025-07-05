package nu.metacraft.faster_minecarts.configs;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.util.List;

public class BlockBoostConfig extends NumberedRegistryEntry<Block> {

	public BlockBoostConfig(List<? extends String> config) {
		super(config, Registries.BLOCK, "\\+");
	}
}

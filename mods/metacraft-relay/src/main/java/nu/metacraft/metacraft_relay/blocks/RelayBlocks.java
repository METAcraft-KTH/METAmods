package nu.metacraft.metacraft_relay.blocks;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import nu.metacraft.metacraft_relay.Relay;
import nu.metacraft.metacraft_relay.blocks.block.RelayBlock;

import java.util.function.Function;

public class RelayBlocks {

	public static final Block RELAY = register(
			"relay", RelayBlock::new,
			AbstractBlock.Settings.copy(Blocks.LODESTONE)
	);

	public static void init() {

	}

	private static Block register(String id, Function<AbstractBlock.Settings, Block> creator, AbstractBlock.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.BLOCK, Relay.getID(id));
		return Registry.register(Registries.BLOCK, key, creator.apply(settings.registryKey(key)));
	}

}

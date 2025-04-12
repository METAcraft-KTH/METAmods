package nu.metacraft.metacraft_relay.blocks;

import net.minecraft.block.*;
import net.minecraft.block.enums.NoteBlockInstrument;
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
			AbstractBlock.Settings.create().mapColor(MapColor.BLACK).instrument(
					NoteBlockInstrument.BASEDRUM
			).requiresTool().strength(50.0F, 1200.0F).luminance(
					(state) -> RelayBlock.getLightLevel(state, 15)
			)
	);

	public static void init() {

	}

	private static Block register(String id, Function<AbstractBlock.Settings, Block> creator, AbstractBlock.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.BLOCK, Relay.getID(id));
		return Registry.register(Registries.BLOCK, key, creator.apply(settings.registryKey(key)));
	}

}

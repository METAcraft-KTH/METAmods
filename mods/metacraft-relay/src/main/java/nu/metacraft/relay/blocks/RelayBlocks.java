package nu.metacraft.relay.blocks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import nu.metacraft.lib.util.RegistrationPair;
import nu.metacraft.relay.Relay;
import nu.metacraft.relay.blocks.block.RelayBlock;

import java.util.function.Function;

public class RelayBlocks {

	public static final RegistrationPair<Block> RELAY = register(
			"relay", RelayBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(
					NoteBlockInstrument.BASEDRUM
			).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).lightLevel(
					(state) -> RelayBlock.getLightLevel(state, 15)
			)
	);

	public static void init() {

	}

	private static RegistrationPair<Block> register(String id, Function<BlockBehaviour.Properties, Block> creator, BlockBehaviour.Properties settings) {
		var key = ResourceKey.create(Registries.BLOCK, Relay.getID(id));
		return new RegistrationPair<>(key, Registry.register(BuiltInRegistries.BLOCK, key, creator.apply(settings.setId(key))));
	}

}

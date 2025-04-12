package nu.metacraft.metacraft_relay.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import nu.metacraft.metacraft_relay.Relay;
import nu.metacraft.metacraft_relay.blocks.entity.RelayBlockEntity;

public class RelayBlockEntities {

	public static final BlockEntityType<RelayBlockEntity> RELAY = register(
			"relay", FabricBlockEntityTypeBuilder.create(
					RelayBlockEntity::new, RelayBlocks.RELAY
			).build()
	);

	public static void init() {

	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(Registries.BLOCK_ENTITY_TYPE, Relay.getID(id), type);
	}

}

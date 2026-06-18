package nu.metacraft.relay.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import nu.metacraft.relay.Relay;
import nu.metacraft.relay.blocks.entity.RelayBlockEntity;

public class RelayBlockEntities {

	public static final BlockEntityType<RelayBlockEntity> RELAY = register(
			"relay", FabricBlockEntityTypeBuilder.create(
					RelayBlockEntity::new, RelayBlocks.RELAY.value()
			).build()
	);

	public static void init() {

	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Relay.getID(id), type);
	}

}

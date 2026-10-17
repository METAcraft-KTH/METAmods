package nu.metacraft.nether_rifts.rift;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import nu.metacraft.nether_rifts.NetherRifts;

public final class RiftBlocks {

	private static final ResourceKey<Block> RIFT_KEY = ResourceKey.create(Registries.BLOCK, NetherRifts.getID("rift"));

	public static final Block RIFT = Registry.register(BuiltInRegistries.BLOCK, RIFT_KEY, new RiftBlock(
			BlockBehaviour.Properties.of().setId(RIFT_KEY).noCollision().noLootTable().noOcclusion().lightLevel(state -> 11)
					.strength(-1.0F, 3_600_000.0F).pushReaction(PushReaction.IMMOVEABLE)
	));

	private RiftBlocks() {}

	public static void init() {
	}
}

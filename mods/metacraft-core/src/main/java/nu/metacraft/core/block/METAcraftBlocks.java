package nu.metacraft.core.block;

import nu.metacraft.core.block.blocks.*;
import nu.metacraft.core.METAcraftCore;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public class METAcraftBlocks {

	public static final Block PORTAL_CORE = register(
			"portal_core", PortalCore::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.END_GATEWAY).noTerrainParticles()
	);
	public static final Block BLACK_HOLE_CORE = register(
			"black_hole_core", BlackHolePortalCore::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.END_GATEWAY).noTerrainParticles()
	);

	public static final Block PORTAL_PADDING = register(
			"portal_padding", PortalPadding::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.END_GATEWAY).noTerrainParticles()
	);

	public static final Block MUSIC_PLAYER = register(
			"music_player", MusicBlock::new,
			BlockBehaviour.Properties.of().strength(
					-1.0f, 3600000.8f
			).noLootTable().noOcclusion().isValidSpawn(
					(state, world, pos, type) -> false
			).noTerrainParticles().pushReaction(PushReaction.BLOCK)
	);

	public static final Block TRAP_SPAWNER = register(
			"trap_spawner", TrapSpawner::new,
			BlockBehaviour.Properties.of().strength(
					-1.0f, 3600000.8f
			).noLootTable().noOcclusion().isValidSpawn(
					(state, world, pos, type) -> false
			)
	);

	public static void init() {
		METAcraftBlockEntities.init();
	}


	private static Block register(String id, Function<BlockBehaviour.Properties, Block> creator, BlockBehaviour.Properties settings) {
		var key = ResourceKey.create(Registries.BLOCK, METAcraftCore.getID(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, creator.apply(settings.setId(key)));
	}
}

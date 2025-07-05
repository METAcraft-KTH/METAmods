package nu.metacraft.core.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import nu.metacraft.core.block.blocks.*;
import nu.metacraft.core.METAcraftCore;

import java.util.function.Function;

public class METAcraftBlocks {

	public static final Block PORTAL_CORE = register(
			"portal_core", PortalCore::new,
			AbstractBlock.Settings.copy(Blocks.END_GATEWAY).noBlockBreakParticles()
	);
	public static final Block BLACK_HOLE_CORE = register(
			"black_hole_core", BlackHolePortalCore::new,
			AbstractBlock.Settings.copy(Blocks.END_GATEWAY).noBlockBreakParticles()
	);

	public static final Block PORTAL_PADDING = register(
			"portal_padding", PortalPadding::new,
			AbstractBlock.Settings.copy(Blocks.END_GATEWAY).noBlockBreakParticles()
	);

	public static final Block MUSIC_PLAYER = register(
			"music_player", MusicBlock::new,
			AbstractBlock.Settings.create().strength(
					-1.0f, 3600000.8f
			).dropsNothing().nonOpaque().allowsSpawning(
					(state, world, pos, type) -> false
			).noBlockBreakParticles().pistonBehavior(PistonBehavior.BLOCK)
	);

	public static final Block TRAP_SPAWNER = register(
			"trap_spawner", TrapSpawner::new,
			AbstractBlock.Settings.create().strength(
					-1.0f, 3600000.8f
			).dropsNothing().nonOpaque().allowsSpawning(
					(state, world, pos, type) -> false
			)
	);

	public static void init() {
		METAcraftBlockEntities.init();
	}


	private static Block register(String id, Function<AbstractBlock.Settings, Block> creator, AbstractBlock.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.BLOCK, METAcraftCore.getID(id));
		return Registry.register(Registries.BLOCK, key, creator.apply(settings.registryKey(key)));
	}
}

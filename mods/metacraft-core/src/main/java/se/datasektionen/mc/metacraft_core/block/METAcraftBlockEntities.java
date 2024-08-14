package se.datasektionen.mc.metacraft_core.block;

import com.google.common.collect.ImmutableSet;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.block.entities.BlackHolePortalEntity;
import se.datasektionen.mc.metacraft_core.block.entities.MusicBlockEntity;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_core.block.entities.TrapSpawnerEntity;

public class METAcraftBlockEntities {

	public static final BlockEntityType<PortalEntity> PORTAL = register(
			"portal", new BlockEntityType<>(
					PortalEntity::new, ImmutableSet.of(METAcraftBlocks.PORTAL_CORE), null
			)
	);
	public static final BlockEntityType<BlackHolePortalEntity> BLACK_HOLE = register(
			"black_hole", new BlockEntityType<>(
					BlackHolePortalEntity::new, ImmutableSet.of(METAcraftBlocks.BLACK_HOLE_CORE), null
			)
	);

	public static final BlockEntityType<MusicBlockEntity> MUSIC_PLAYER = register(
			"music_player", new BlockEntityType<>(
					MusicBlockEntity::new, ImmutableSet.of(METAcraftBlocks.MUSIC_PLAYER), null
			)
	);

	public static final BlockEntityType<TrapSpawnerEntity> TRAP_SPAWNER = register(
			"trap_spawner", new BlockEntityType<>(
					TrapSpawnerEntity::new, ImmutableSet.of(METAcraftBlocks.TRAP_SPAWNER), null
			)
	);

	public static void init() {

	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(Registries.BLOCK_ENTITY_TYPE, METAcraftCore.getID(id), type);
	}

}

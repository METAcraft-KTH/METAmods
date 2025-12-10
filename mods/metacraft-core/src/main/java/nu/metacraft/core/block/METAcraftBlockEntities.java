package nu.metacraft.core.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.block.entities.BlackHolePortalEntity;
import nu.metacraft.core.block.entities.MusicBlockEntity;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.core.block.entities.TrapSpawnerEntity;

public class METAcraftBlockEntities {

	public static final BlockEntityType<PortalEntity> PORTAL = register(
			"portal", FabricBlockEntityTypeBuilder.create(
					PortalEntity::new, METAcraftBlocks.PORTAL_CORE
			).build()
	);
	public static final BlockEntityType<BlackHolePortalEntity> BLACK_HOLE = register(
			"black_hole", FabricBlockEntityTypeBuilder.create(
					BlackHolePortalEntity::new, METAcraftBlocks.BLACK_HOLE_CORE
			).build()
	);

	public static final BlockEntityType<MusicBlockEntity> MUSIC_PLAYER = register(
			"music_player", FabricBlockEntityTypeBuilder.create(
					MusicBlockEntity::new, METAcraftBlocks.MUSIC_PLAYER
			).build()
	);

	public static final BlockEntityType<TrapSpawnerEntity> TRAP_SPAWNER = register(
			"trap_spawner", FabricBlockEntityTypeBuilder.create(
					TrapSpawnerEntity::new, METAcraftBlocks.TRAP_SPAWNER
			).build()
	);

	public static void init() {

	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, METAcraftCore.getID(id), type);
	}

}

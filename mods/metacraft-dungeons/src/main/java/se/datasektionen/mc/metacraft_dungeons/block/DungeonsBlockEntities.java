package se.datasektionen.mc.metacraft_dungeons.block;

import com.google.common.collect.ImmutableSet;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.BlackHolePortalEntity;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.DataBlockRegistry;

public class DungeonsBlockEntities {

	public static final BlockEntityType<PortalEntity> PORTAL = register(
			"portal", new BlockEntityType<>(
					PortalEntity::new, ImmutableSet.of(DungeonBlocks.PORTAL), null
			)
	);

	public static final BlockEntityType<DungeonEntranceEntity> DUNGEON_ENTRANCE = register(
			"dungeon_entrance", new BlockEntityType<>(
					DungeonEntranceEntity::new, ImmutableSet.of(DungeonBlocks.DUNGEON_ENTRANCE), null
			)
	);
	public static final BlockEntityType<BlackHolePortalEntity> BLACK_HOLE = register(
			"black_hole", new BlockEntityType<>(
					BlackHolePortalEntity::new, ImmutableSet.of(DungeonBlocks.BLACK_HOLE), null
			)
	);


	public static void init() {
		PolymerBlockUtils.registerBlockEntity(PORTAL, DUNGEON_ENTRANCE, BLACK_HOLE);
		DataBlockRegistry.init();
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		return Registry.register(Registries.BLOCK_ENTITY_TYPE, METAcraftDungeons.getID(id), type);
	}

}

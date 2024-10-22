package se.datasektionen.mc.metacraft_season_4.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.metacraft_core.METAcraftCore;

public class Season4BlockEntities {

	public static void init() {

	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(Registries.BLOCK_ENTITY_TYPE, METAcraftCore.getID(id), type);
	}

}

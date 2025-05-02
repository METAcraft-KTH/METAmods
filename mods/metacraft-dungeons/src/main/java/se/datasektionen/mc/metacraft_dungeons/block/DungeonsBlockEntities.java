package se.datasektionen.mc.metacraft_dungeons.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.callbacks.PortalTargetValidEvent;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks.DataBlockRegistry;
import se.datasektionen.mc.metacraft_dungeons.dungeons.portal_data.DungeonPortalTargets;

public class DungeonsBlockEntities {


	public static void init() {
		DataBlockRegistry.init();
		DungeonPortalTargets.init();
		PortalTargetValidEvent.EVENT.register((targetDim, targetPos, portal, teleporting, isCurrentlyValid) -> {
			if (!isCurrentlyValid) return false;
			if (targetDim != portal.getWorld() && DungeonData.getIfPresent(targetDim).map(DungeonData::isResetting).orElse(false)) {
				if (teleporting instanceof ServerPlayerEntity player) {
					player.sendMessage(Text.literal("Dungeon dimension resetting, please wait."));
				}
				return false;
			}
			return true;
		});
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(Registries.BLOCK_ENTITY_TYPE, METAcraftDungeons.getID(id), type);
	}

}

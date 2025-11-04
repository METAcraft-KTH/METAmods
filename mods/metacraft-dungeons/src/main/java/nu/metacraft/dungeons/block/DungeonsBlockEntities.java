package nu.metacraft.dungeons.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import nu.metacraft.core.callbacks.PortalTargetValidEvent;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.dungeons.dungeons.DungeonData;
import nu.metacraft.dungeons.dungeons.datablocks.DataBlockRegistry;
import nu.metacraft.dungeons.dungeons.portal_data.DungeonPortalTargets;

public class DungeonsBlockEntities {


	public static void init() {
		DataBlockRegistry.init();
		DungeonPortalTargets.init();
		PortalTargetValidEvent.EVENT.register((targetDim, targetPos, portal, teleporting, isCurrentlyValid) -> {
			if (!isCurrentlyValid) return false;
			if (targetDim != portal.getLevel() && DungeonData.getIfPresent(targetDim).map(DungeonData::isResetting).orElse(false)) {
				if (teleporting instanceof ServerPlayer player) {
					player.sendSystemMessage(Component.literal("Dungeon dimension resetting, please wait."));
				}
				return false;
			}
			return true;
		});
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
		PolymerBlockUtils.registerBlockEntity(type);
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, METAcraftDungeons.getID(id), type);
	}

}

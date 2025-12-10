package nu.metacraft.loot_containers;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import nu.metacraft.loot_containers.containers.LootAccess;
import nu.metacraft.loot_containers.containers.LootContainerData;

public class Events {

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			LootContainerData.getInstance(server).tick();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide() && LootAccess.getInventoryFromEntity(entity).isPresent()) {
				LootContainerData.getInstance(world.getServer()).getLootContainers(
						world.dimension(), entity.getUUID()
				).forEach(
						container -> container.getSecond().onOpen((ServerPlayer) player)
				);
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClientSide()) {
				var blockEntity = world.getBlockEntity(hitResult.getBlockPos());
				if (LootAccess.getInventoryFromBlockEntity(blockEntity).isPresent()) {
					LootContainerData.getInstance(world.getServer()).getLootContainers(
							world.dimension(), hitResult.getBlockPos()
					).forEach(
							container -> container.getSecond().onOpen((ServerPlayer) player)
					);
				}
			}
			return InteractionResult.PASS;
		});
	}

}

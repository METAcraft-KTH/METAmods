package nu.metacraft.loot_containers;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import nu.metacraft.loot_containers.containers.LootAccess;
import nu.metacraft.loot_containers.containers.LootContainerData;

public class Events {

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			LootContainerData.getInstance(server).tick();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient() && LootAccess.getInventoryFromEntity(entity).isPresent()) {
				LootContainerData.getInstance(world.getServer()).getLootContainers(
						world.getRegistryKey(), entity.getUuid()
				).forEach(
						container -> container.getSecond().onOpen((ServerPlayerEntity) player)
				);
			}
			return ActionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient()) {
				var blockEntity = world.getBlockEntity(hitResult.getBlockPos());
				if (LootAccess.getInventoryFromBlockEntity(blockEntity).isPresent()) {
					LootContainerData.getInstance(world.getServer()).getLootContainers(
							world.getRegistryKey(), hitResult.getBlockPos()
					).forEach(
							container -> container.getSecond().onOpen((ServerPlayerEntity) player)
					);
				}
			}
			return ActionResult.PASS;
		});
	}

}

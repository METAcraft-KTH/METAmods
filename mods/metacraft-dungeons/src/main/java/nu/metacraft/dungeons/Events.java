package nu.metacraft.dungeons;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import nu.metacraft.core.block.entities.MusicBlockEntity;
import nu.metacraft.dungeons.dungeons.DungeonData;
import nu.metacraft.dungeons.util.ChunkHelper;

public class Events {

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			DungeonData.getIfPresent(world).ifPresent(DungeonData::tick);
		});
		ServerWorldEvents.LOAD.register(
				(server, world) -> {
					if (world.dimension() == Dimensions.DUNGEONS) {
						DungeonData.getInstance(world); //Init data for dungeons dimension.
					}
				}
		);
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
			if (blockEntity instanceof MusicBlockEntity musicBlock) {
				DungeonData.getIfPresent(world).ifPresent(data -> data.loadMusicBlock(musicBlock));
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
			if (blockEntity instanceof MusicBlockEntity musicBlock) {
				DungeonData.getIfPresent(world).ifPresent(data -> data.unloadMusicBlock(musicBlock));
			}
		});
		ChunkHelper.init();
	}

}

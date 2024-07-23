package se.datasektionen.mc.metacraft_dungeons;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import se.datasektionen.mc.metacraft_core.block.entities.MusicBlockEntity;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_dungeons.util.ChunkHelper;

public class Events {

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			DungeonData.getIfPresent(world).ifPresent(DungeonData::tick);
		});
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

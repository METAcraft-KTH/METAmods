package nu.metacraft.dungeons;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import nu.metacraft.core.block.entities.MusicBlockEntity;
import nu.metacraft.dungeons.dungeons.DungeonData;
import nu.metacraft.dungeons.util.ChunkHelper;

public class Events {

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(world -> {
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

package se.datasektionen.mc.metacraft_dungeons.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ChunkHelper {

	private static final Lock lock = new ReentrantLock();
	private static boolean init = false;
	private static final Map<ServerWorld, Long2ObjectMap<Consumer<Optional<Chunk>>>> whenChunkCompleteMap = new HashMap<>();

	public static void whenChunkReady(ServerWorld world, ChunkPos pos, ChunkStatus status, Consumer<Optional<Chunk>> chunkAction) {
		var c = world.getChunk(pos.x, pos.z, status, false);
		if (c != null) {
			chunkAction.accept(Optional.of(c));
			return;
		}

		lock.lock();
		var worldMap = whenChunkCompleteMap.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>());
		long key = pos.toLong();
		if (worldMap.containsKey(key)) {
			worldMap.put(key, worldMap.get(key).andThen(chunkAction));
		} else {
			worldMap.put(key, chunkAction);
		}
		lock.unlock();
	}

	public static void init() {
		if (init) return;
		init = true;
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			lock.lock();
			if (whenChunkCompleteMap.containsKey(world)) {
				var worldMap = whenChunkCompleteMap.get(world);
				if (worldMap.containsKey(chunk.getPos().toLong())) {
					worldMap.remove(chunk.getPos().toLong()).accept(Optional.of(chunk));
				}
			}
			lock.unlock();
		});
		ServerWorldEvents.UNLOAD.register((server, world) -> {
			lock.lock();
			var chunks = whenChunkCompleteMap.remove(world);
			lock.unlock();
			if (chunks != null) {
				chunks.forEach((pos, action) -> action.accept(Optional.empty()));
			}
		});
	}

}

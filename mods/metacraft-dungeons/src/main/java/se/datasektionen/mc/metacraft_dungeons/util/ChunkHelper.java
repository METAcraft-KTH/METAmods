package se.datasektionen.mc.metacraft_dungeons.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ChunkHelper {

	private static final Lock lock = new ReentrantLock();
	private static boolean init = false;
	private static final Long2ObjectMap<Consumer<Chunk>> whenChunkCompleteMap = new Long2ObjectOpenHashMap<>();

	public static void whenChunkReady(ServerWorld world, ChunkPos pos, ChunkStatus status, Consumer<Chunk> chunkAction) {
		var c = world.getChunk(pos.x, pos.z, status, false);
		if (c != null) {
			chunkAction.accept(c);
			return;
		}

		lock.lock();
		long key = pos.toLong();
		if (whenChunkCompleteMap.containsKey(key)) {
			whenChunkCompleteMap.put(key, whenChunkCompleteMap.get(key).andThen(chunkAction));
		} else {
			whenChunkCompleteMap.put(key, chunkAction);
		}
		lock.unlock();
	}

	public static void init() {
		if (init) return;
		init = true;
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			lock.lock();
			if (whenChunkCompleteMap.containsKey(chunk.getPos().toLong())) {
				whenChunkCompleteMap.remove(chunk.getPos().toLong()).accept(chunk);
			}
			lock.unlock();
		});
	}

}

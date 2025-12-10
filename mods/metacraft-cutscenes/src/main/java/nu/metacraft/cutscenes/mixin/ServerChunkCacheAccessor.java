package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {

	@Accessor("mainThreadProcessor")
	ServerChunkCache.MainThreadExecutor getMainThreadExecutor();

	@Accessor
	@Mutable
	void setChunkMap(ChunkMap chunkLoadingManager);

	@Accessor
	@Mutable
	void setDistanceManager(DistanceManager chunkLoadingManager);

	@Accessor
	@Mutable
	void setLightEngine(ThreadedLevelLightEngine chunkLoadingManager);

	@Mutable
	@Accessor
	void setTicketStorage(TicketStorage ticketManager);

}

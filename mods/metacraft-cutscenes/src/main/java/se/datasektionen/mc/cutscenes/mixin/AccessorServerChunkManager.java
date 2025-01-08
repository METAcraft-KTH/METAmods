package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerLightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkManager.class)
public interface AccessorServerChunkManager {

	@Accessor("mainThreadExecutor")
	ServerChunkManager.MainThreadExecutor getMainThreadExecutor();

	@Accessor
	@Mutable
	void setChunkLoadingManager(ServerChunkLoadingManager chunkLoadingManager);

	@Accessor
	@Mutable
	void setTicketManager(ChunkTicketManager chunkLoadingManager);

	@Accessor
	@Mutable
	void setLightingProvider(ServerLightingProvider chunkLoadingManager);

}

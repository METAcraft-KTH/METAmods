package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.server.world.*;
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
	void setLevelManager(ChunkLevelManager chunkLoadingManager);

	@Accessor
	@Mutable
	void setLightingProvider(ServerLightingProvider chunkLoadingManager);

	@Mutable
	@Accessor
	void setTicketManager(ChunkTicketManager ticketManager);

}

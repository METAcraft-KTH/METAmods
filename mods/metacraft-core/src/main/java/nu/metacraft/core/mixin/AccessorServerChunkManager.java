package nu.metacraft.core.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface AccessorServerChunkManager {

	@Invoker
	ChunkHolder callGetVisibleChunkIfPresent(long pos);

}

package nu.metacraft.core.mixin;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkManager.class)
public interface AccessorServerChunkManager {

	@Invoker
	ChunkHolder callGetChunkHolder(long pos);

}

package nu.metacraft.cutscenes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(ChunkHolder.class)
public interface ChunkHolderAccessor {

	@Accessor
	void setTickingChunkFuture(CompletableFuture<ChunkResult<LevelChunk>> tickingFuture);

}

package nu.metacraft.cutscenes.mixin;

import net.minecraft.server.world.OptionalChunk;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(AbstractChunkHolder.class)
public interface AccessorAbstractChunkHolder {

	@Accessor
	void setStatus(ChunkStatus status);

	@Accessor
	AtomicReference<ChunkStatus> getCurrentStatus();

	@Accessor
	AtomicReferenceArray<CompletableFuture<OptionalChunk<Chunk>>> getChunkFuturesByStatus();

}

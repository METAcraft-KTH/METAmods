package nu.metacraft.cutscenes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

@Mixin(GenerationChunkHolder.class)
public interface GenerationChunkHolderAccessor {

	@Accessor
	void setHighestAllowedStatus(ChunkStatus status);

	@Accessor
	AtomicReference<ChunkStatus> getStartedWork();

	@Accessor
	AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> getFutures();

}

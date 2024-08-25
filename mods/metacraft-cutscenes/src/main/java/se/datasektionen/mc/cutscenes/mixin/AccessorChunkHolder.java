package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkHolder.class)
public interface AccessorChunkHolder {

	@Accessor
	void setTickingFuture(CompletableFuture<OptionalChunk<WorldChunk>> tickingFuture);

}

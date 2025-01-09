package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkLightProvider.class)
public interface AccessorChunkLightProvider<M extends ChunkToNibbleArrayMap<M>, S extends LightStorage<M>>  {

	@Accessor
	S getLightStorage();

	@Mutable
	@Accessor
	void setLightStorage(S storage);
}

package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightStorage.class)
public interface AccessorLightStorage<M extends ChunkToNibbleArrayMap<M>> {

	@Accessor
	M getUncachedStorage();

	@Accessor
	M getStorage();

	@Accessor
	void setUncachedStorage(M storage);

	@Mutable
	@Accessor
	void setStorage(M storage);

}

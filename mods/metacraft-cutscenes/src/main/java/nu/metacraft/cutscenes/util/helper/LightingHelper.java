package nu.metacraft.cutscenes.util.helper;

import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.*;
import nu.metacraft.cutscenes.mixin.AccessorChunkLightProvider;
import nu.metacraft.cutscenes.mixin.AccessorLightingProvider;

public class LightingHelper {

	public static ChunkLightProvider<?, SkyLightStorage> getSkyLightProvider(LightingProvider provider) {
		return (ChunkLightProvider<?, SkyLightStorage>) ((AccessorLightingProvider) provider).getSkyLightProvider();
	}

	public static ChunkLightProvider<? extends ChunkToNibbleArrayMap<?>, BlockLightStorage> getBlockLightProvider(LightingProvider provider) {
		return (ChunkLightProvider<?, BlockLightStorage>) ((AccessorLightingProvider) provider).getBlockLightProvider();
	}

	public static <S extends LightStorage<?>> S getLightStorage(ChunkLightProvider<?, S> provider) {
		return ((AccessorChunkLightProvider<?, S>) provider).getLightStorage();
	}

	public static SkyLightStorage getSkyLightStorage(LightingProvider provider) {
		return getLightStorage(getSkyLightProvider(provider));
	}

	public static BlockLightStorage getBlockLightStorage(LightingProvider provider) {
		return getLightStorage(getBlockLightProvider(provider));
	}

}

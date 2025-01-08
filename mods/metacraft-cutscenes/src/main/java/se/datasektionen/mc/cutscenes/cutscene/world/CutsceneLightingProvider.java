package se.datasektionen.mc.cutscenes.cutscene.world;

import net.minecraft.server.world.ChunkTaskScheduler;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.util.thread.SimpleConsecutiveExecutor;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.BlockLightStorage;
import net.minecraft.world.chunk.light.LightStorage;
import net.minecraft.world.chunk.light.SkyLightStorage;
import se.datasektionen.mc.cutscenes.mixin.AccessorChunkLightProvider;
import se.datasektionen.mc.cutscenes.mixin.AccessorLightStorage;
import se.datasektionen.mc.cutscenes.mixin.AccessorLightingProvider;

public class CutsceneLightingProvider extends ServerLightingProvider {
	public CutsceneLightingProvider(
			ChunkProvider chunkProvider, CutsceneChunkLoadingManager chunkLoadingManager,
			boolean hasSkyLight, SimpleConsecutiveExecutor processor,
			ChunkTaskScheduler executor
	) {
		super(chunkProvider, chunkLoadingManager, hasSkyLight, processor, executor);

		var acc = (AccessorLightingProvider) this;
		var block = ((AccessorChunkLightProvider<?, BlockLightStorage>) acc.getBlockLightProvider()).getLightStorage();
		var sky = ((AccessorChunkLightProvider<?, SkyLightStorage>) acc.getSkyLightProvider()).getLightStorage();


		var normalProvider = chunkLoadingManager.getCutsceneWorld().getActualWorld().getLightingProvider();

		acc = (AccessorLightingProvider) normalProvider;
		var normalBlock = ((AccessorChunkLightProvider<?, BlockLightStorage>) acc.getBlockLightProvider()).getLightStorage();
		var normalSky = ((AccessorChunkLightProvider<?, SkyLightStorage>) acc.getSkyLightProvider()).getLightStorage();

		copyData(normalBlock, block);
		copyData(normalSky, sky);
	}

	private static  <M extends ChunkToNibbleArrayMap<M>> void copyData(
			LightStorage<M> prev, LightStorage<M> next
	) {
		copyData(((AccessorLightStorage<M>) prev), (AccessorLightStorage<M>) next);
	}

	private static  <M extends ChunkToNibbleArrayMap<M>> void copyData(
			AccessorLightStorage<M> prev, AccessorLightStorage<M> next
	) {
		next.setUncachedStorage(prev.getUncachedStorage().copy());
		next.setStorage(prev.getStorage().copy());
	}
}

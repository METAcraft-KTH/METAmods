package nu.metacraft.cutscenes.cutscene.world;

import net.minecraft.server.world.ChunkTaskScheduler;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.util.thread.SimpleConsecutiveExecutor;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.BlockLightStorage;
import net.minecraft.world.chunk.light.SkyLightStorage;
import nu.metacraft.cutscenes.mixin.AccessorChunkLightProvider;
import nu.metacraft.cutscenes.util.helper.LightingHelper;

public class CutsceneLightingProvider extends ServerLightingProvider {
	public CutsceneLightingProvider(
			ChunkProvider chunkProvider, CutsceneChunkLoadingManager chunkLoadingManager,
			boolean hasSkyLight, SimpleConsecutiveExecutor processor,
			ChunkTaskScheduler executor
	) {
		super(chunkProvider, chunkLoadingManager, hasSkyLight, processor, executor);

		var blockProvider = LightingHelper.getBlockLightProvider(this);
		var skyProvider = LightingHelper.getSkyLightProvider(this);

		((AccessorChunkLightProvider<?,BlockLightStorage>) blockProvider).setLightStorage(
				new CutsceneBlockLightStorage(chunkProvider)
		);
		if (skyProvider != null) {
			((AccessorChunkLightProvider<?,SkyLightStorage>) skyProvider).setLightStorage(
					new CutsceneSkyLightStorage(chunkProvider)
			);
		}
	}
}

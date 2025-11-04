package nu.metacraft.cutscenes.cutscene.world;

import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import nu.metacraft.cutscenes.mixin.LightEngineAccessor;
import nu.metacraft.cutscenes.util.helper.LightingHelper;

public class CutsceneLightingProvider extends ThreadedLevelLightEngine {
	public CutsceneLightingProvider(
			LightChunkGetter chunkProvider, CutsceneChunkLoadingManager chunkLoadingManager,
			boolean hasSkyLight, ConsecutiveExecutor processor,
			ChunkTaskDispatcher executor
	) {
		super(chunkProvider, chunkLoadingManager, hasSkyLight, processor, executor);

		var blockProvider = LightingHelper.getBlockLightProvider(this);
		var skyProvider = LightingHelper.getSkyLightProvider(this);

		((LightEngineAccessor<?,BlockLightSectionStorage>) blockProvider).setStorage(
				new CutsceneBlockLightStorage(chunkProvider)
		);
		if (skyProvider != null) {
			((LightEngineAccessor<?,SkyLightSectionStorage>) skyProvider).setStorage(
					new CutsceneSkyLightStorage(chunkProvider)
			);
		}
	}
}

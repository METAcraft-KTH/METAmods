package se.metacraft.cutscenes.cutscene.world;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import se.metacraft.cutscenes.util.helper.LightingHelper;

public class CutsceneBlockLightStorage extends BlockLightSectionStorage {
	protected CutsceneBlockLightStorage(LightChunkGetter chunkProvider) {
		super(chunkProvider);
	}

	@Override
	protected DataLayer createDataLayer(long sectionPos) {
		if (queuedSections.containsKey(sectionPos)) return super.createDataLayer(sectionPos);
		var provider = ((CutsceneChunkCache) chunkSource).getCutsceneWorld().getActualWorld().getLightEngine();
		var blockProvider = LightingHelper.getBlockLightProvider(provider);
		if (blockProvider == null) return super.createDataLayer(sectionPos);
		var storage = LightingHelper.getLightStorage(blockProvider).getDataLayerData(sectionPos);
		return storage != null ? storage.copy() : super.createDataLayer(sectionPos);
	}
}

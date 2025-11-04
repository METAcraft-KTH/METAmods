package nu.metacraft.cutscenes.cutscene.world;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import nu.metacraft.cutscenes.util.helper.LightingHelper;

public class CutsceneSkyLightStorage extends SkyLightSectionStorage {
	protected CutsceneSkyLightStorage(LightChunkGetter chunkProvider) {
		super(chunkProvider);
	}

	@Override
	protected DataLayer createDataLayer(long sectionPos) {
		if (queuedSections.containsKey(sectionPos)) return super.createDataLayer(sectionPos);
		var provider = ((CutsceneChunkManager) chunkSource).getCutsceneWorld().getActualWorld().getLightEngine();
		var storage = LightingHelper.getSkyLightStorage(provider).getDataLayerData(sectionPos);
		return storage != null ? storage.copy() : super.createDataLayer(sectionPos);
	}
}

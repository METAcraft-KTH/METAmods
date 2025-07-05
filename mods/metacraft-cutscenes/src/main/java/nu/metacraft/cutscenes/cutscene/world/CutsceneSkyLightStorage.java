package nu.metacraft.cutscenes.cutscene.world;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.SkyLightStorage;
import nu.metacraft.cutscenes.util.helper.LightingHelper;

public class CutsceneSkyLightStorage extends SkyLightStorage {
	protected CutsceneSkyLightStorage(ChunkProvider chunkProvider) {
		super(chunkProvider);
	}

	@Override
	protected ChunkNibbleArray createSection(long sectionPos) {
		if (queuedSections.containsKey(sectionPos)) return super.createSection(sectionPos);
		var provider = ((CutsceneChunkManager) chunkProvider).getCutsceneWorld().getActualWorld().getLightingProvider();
		var storage = LightingHelper.getSkyLightStorage(provider).getLightSection(sectionPos);
		return storage != null ? storage.copy() : super.createSection(sectionPos);
	}
}

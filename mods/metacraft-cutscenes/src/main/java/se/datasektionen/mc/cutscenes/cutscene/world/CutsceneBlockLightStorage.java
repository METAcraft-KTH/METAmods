package se.datasektionen.mc.cutscenes.cutscene.world;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.BlockLightStorage;
import se.datasektionen.mc.cutscenes.util.helper.LightingHelper;

public class CutsceneBlockLightStorage extends BlockLightStorage {
	protected CutsceneBlockLightStorage(ChunkProvider chunkProvider) {
		super(chunkProvider);
	}

	@Override
	protected ChunkNibbleArray createSection(long sectionPos) {
		if (queuedSections.containsKey(sectionPos)) return super.createSection(sectionPos);
		var provider = ((CutsceneChunkManager) chunkProvider).getCutsceneWorld().getActualWorld().getLightingProvider();
		var storage = LightingHelper.getBlockLightStorage(provider).getLightSection(sectionPos);
		return storage != null ? storage.copy() : super.createSection(sectionPos);
	}
}

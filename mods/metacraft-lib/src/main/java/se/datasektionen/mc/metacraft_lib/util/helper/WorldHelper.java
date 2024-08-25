package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorServerChunkLoadingManager;

@SuppressWarnings("unused")
public class WorldHelper {

	public static WorldGenerationProgressListener getGenerationProgressListener(ServerWorld world) {
		return getGenerationProgressListener(world.getChunkManager().chunkLoadingManager);
	}

	public static WorldGenerationProgressListener getGenerationProgressListener(ServerChunkLoadingManager manager) {
		return ((AccessorServerChunkLoadingManager) manager).getWorldGenerationProgressListener();
	}

}

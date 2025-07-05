package nu.metacraft.lib.util.helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.level.storage.LevelStorage;
import nu.metacraft.lib.mixin.AccessorMinecraftServer;
import nu.metacraft.lib.mixin.AccessorServerChunkLoadingManager;

@SuppressWarnings("unused")
public class WorldHelper {

	public static WorldGenerationProgressListener getGenerationProgressListener(ServerWorld world) {
		return getGenerationProgressListener(world.getChunkManager().chunkLoadingManager);
	}

	public static WorldGenerationProgressListener getGenerationProgressListener(ServerChunkLoadingManager manager) {
		return ((AccessorServerChunkLoadingManager) manager).getWorldGenerationProgressListener();
	}

	public static LevelStorage.Session getSession(MinecraftServer server) {
		return ((AccessorMinecraftServer) server).getSession();
	}

}

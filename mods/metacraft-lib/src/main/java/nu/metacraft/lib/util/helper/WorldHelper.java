package nu.metacraft.lib.util.helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import nu.metacraft.lib.mixin.AccessorMinecraftServer;

@SuppressWarnings("unused")
public class WorldHelper {

	public static LevelStorageSource.LevelStorageAccess getSession(MinecraftServer server) {
		return ((AccessorMinecraftServer) server).getStorageSource();
	}

}

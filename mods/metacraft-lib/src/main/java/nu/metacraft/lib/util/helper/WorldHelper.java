package nu.metacraft.lib.util.helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorage;
import nu.metacraft.lib.mixin.AccessorMinecraftServer;

@SuppressWarnings("unused")
public class WorldHelper {

	public static LevelStorage.Session getSession(MinecraftServer server) {
		return ((AccessorMinecraftServer) server).getSession();
	}

}

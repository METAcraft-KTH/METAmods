package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.metacraft_lib.compat.VanishCompat;

import java.util.UUID;

@SuppressWarnings("unused")
public class VanishHelper {

	public static boolean isVanished(UUID id, MinecraftServer server) {
		if (IsLoaded.VANISH.isLoaded()) {
			return VanishCompat.isVanished(id, server);
		}
		return false;
	}

	public static boolean isVanished(Entity entity) {
		if (IsLoaded.VANISH.isLoaded()) {
			return VanishCompat.isVanished(entity);
		}
		return false;
	}

}

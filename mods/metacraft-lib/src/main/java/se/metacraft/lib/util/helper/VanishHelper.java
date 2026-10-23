package se.metacraft.lib.util.helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import se.metacraft.lib.compat.IsLoaded;
import se.metacraft.lib.compat.VanishCompat;

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

package nu.metacraft.lib.compat;

import me.drex.vanish.api.VanishAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

public class VanishCompat {

	public static boolean isVanished(UUID id, MinecraftServer server) {
		return VanishAPI.isVanished(server, id);
	}

	public static boolean isVanished(Entity entity) {
		return VanishAPI.isVanished(entity);
	}

}

package nu.metacraft.moderation.compat;

import me.drex.vanish.api.VanishAPI;
import net.minecraft.server.network.ServerPlayerEntity;

public class Vanish {

	public static void setVanishState(ServerPlayerEntity player, boolean vanish) {
		VanishAPI.setVanish(player, vanish);
	}

}

package nu.metacraft.moderation.compat;

import me.drex.vanish.api.VanishAPI;
import net.minecraft.server.level.ServerPlayer;

public class Vanish {

	public static void setVanishState(ServerPlayer player, boolean vanish) {
		VanishAPI.setVanish(player, vanish);
	}

}

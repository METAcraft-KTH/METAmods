package nu.metacraft.discord_chat_fixer.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;

public class CoreCompat {

	public static boolean isMETAcraftPlayer(Entity entity) {
		return entity instanceof PlayerMob;
	}

	public static GameProfile getFromMETAcraftPlayer(Entity entity) {
		return ((PlayerMob) entity).getProfile();
	}

}

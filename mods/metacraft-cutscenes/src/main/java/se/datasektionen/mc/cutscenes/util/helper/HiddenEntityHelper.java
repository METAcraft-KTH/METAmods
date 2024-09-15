package se.datasektionen.mc.cutscenes.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

public class HiddenEntityHelper {

	public static boolean isHiddenFrom(Entity entity, ServerPlayerEntity player) {
		if (entity instanceof ServerPlayerEntity p) {
			if (p == player) return false;
			var scene = CutsceneHelper.getCutscene(p);
			if (scene.isPresent()) {
				if (scene.get().isPlayerHiddenFrom(p, player)) {
					return true;
				}
			}
		}
		return false;
	}

}

package nu.metacraft.cutscenes.util.helper;

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
		} else {
			var scene = CutsceneHelper.getCutscene(player);
			if (scene.isPresent() && entity.getWorld() != scene.get().getCutsceneWorld()) {
				if (scene.get().getCutsceneWorld().getEntityManager().isHidden(entity.getUuid())) {
					return true;
				}
			}
		}
		return false;
	}

}

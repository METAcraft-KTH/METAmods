package nu.metacraft.cutscenes.util.helper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class HiddenEntityHelper {

	public static boolean isHiddenFrom(Entity entity, ServerPlayer player) {
		if (entity instanceof ServerPlayer p) {
			if (p == player) return false;
			var scene = CutsceneHelper.getCutscene(p);
			if (scene.isPresent()) {
				return scene.get().isPlayerHiddenFrom(p, player);
			}
		} else {
			var scene = CutsceneHelper.getCutscene(player);
			if (scene.isPresent() && entity.level() != scene.get().getCutsceneWorld()) {
				return scene.get().getCutsceneWorld().getEntityManager().isHidden(entity.getUUID());
			}
		}
		return false;
	}

}

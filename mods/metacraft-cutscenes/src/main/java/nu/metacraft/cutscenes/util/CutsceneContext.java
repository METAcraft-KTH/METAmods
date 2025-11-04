package nu.metacraft.cutscenes.util;

import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.core.util.RefContext;

public record CutsceneContext(ServerPlayer player, CutsceneInstance cutscene) {

	public RefContext getRefContext() {
		return cutscene.createRefContext(player);
	}

}

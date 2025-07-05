package nu.metacraft.cutscenes.util;

import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.util.RefContext;

public record CutsceneContext(ServerPlayerEntity player, CutsceneInstance cutscene) {

	public RefContext getRefContext() {
		return cutscene.createRefContext(player);
	}

}

package se.datasektionen.mc.cutscenes.util;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.metacraft_core.util.RefContext;

public record CutsceneContext(ServerPlayerEntity player, CutsceneInstance cutscene) {

	public RefContext getRefContext() {
		return cutscene.createRefContext(player);
	}

}

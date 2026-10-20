package nu.metacraft.cutscenes.util;

import net.minecraft.world.entity.Entity;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.util.RefContext;

public record CutsceneContext(Entity entity, CutsceneInstance cutscene) {

	public RefContext getRefContext() {
		return cutscene.createRefContext(entity);
	}

}

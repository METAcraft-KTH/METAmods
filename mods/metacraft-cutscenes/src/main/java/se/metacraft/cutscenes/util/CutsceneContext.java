package se.metacraft.cutscenes.util;

import net.minecraft.world.entity.Entity;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.core.util.RefContext;

public record CutsceneContext(Entity entity, CutsceneInstance cutscene) {

	public RefContext getRefContext() {
		return cutscene.createRefContext(entity);
	}

}

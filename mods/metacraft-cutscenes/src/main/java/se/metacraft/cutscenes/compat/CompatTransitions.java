package se.metacraft.cutscenes.compat;

import se.metacraft.cutscenes.compat.resource_pack.ResourcePackTransition;
import se.metacraft.lib.compat.IsLoaded;

public class CompatTransitions {

	public static void init() {
		if (IsLoaded.RESOURCE_PACKS.isLoaded()) {
			ResourcePackTransition.init();
		}
	}

}

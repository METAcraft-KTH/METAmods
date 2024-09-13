package se.datasektionen.mc.cutscenes.compat;

import se.datasektionen.mc.cutscenes.compat.resource_pack.ResourcePackTransition;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;

public class CompatTransitions {

	public static void init() {
		if (IsLoaded.RESOURCE_PACKS.isLoaded()) {
			ResourcePackTransition.init();
		}
	}

}

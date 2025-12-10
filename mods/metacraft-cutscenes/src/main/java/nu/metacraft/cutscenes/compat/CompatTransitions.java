package nu.metacraft.cutscenes.compat;

import nu.metacraft.cutscenes.compat.resource_pack.ResourcePackTransition;
import nu.metacraft.lib.compat.IsLoaded;

public class CompatTransitions {

	public static void init() {
		if (IsLoaded.RESOURCE_PACKS.isLoaded()) {
			ResourcePackTransition.init();
		}
	}

}

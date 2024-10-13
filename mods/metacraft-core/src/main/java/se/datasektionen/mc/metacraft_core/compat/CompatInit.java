package se.datasektionen.mc.metacraft_core.compat;

import se.datasektionen.mc.metacraft_core.compat.leukocyte.LeukocyteCompat;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;

public class CompatInit {

	public static void init() {
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteCompat.init();
		}
	}

}

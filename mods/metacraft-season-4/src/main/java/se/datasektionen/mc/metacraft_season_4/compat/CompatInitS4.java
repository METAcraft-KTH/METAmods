package se.datasektionen.mc.metacraft_season_4.compat;

import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.metacraft_season_4.compat.leukocyte.LeukocyteCompat;

public class CompatInitS4 {

	public static void init() {
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteCompat.init();
		}
	}

}

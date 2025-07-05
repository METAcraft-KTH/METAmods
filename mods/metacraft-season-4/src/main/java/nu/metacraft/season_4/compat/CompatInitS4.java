package nu.metacraft.season_4.compat;

import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.season_4.compat.leukocyte.LeukocyteCompat;

public class CompatInitS4 {

	public static void init() {
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteCompat.init();
		}
	}

}

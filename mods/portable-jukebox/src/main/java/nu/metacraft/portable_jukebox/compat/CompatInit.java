package nu.metacraft.portable_jukebox.compat;

import nu.metacraft.lib.compat.IsLoaded;

public class CompatInit {

	public static void init() {
		if (IsLoaded.LEDGER.isLoaded()) {
			LedgerCompat.init();
		}
	}

}

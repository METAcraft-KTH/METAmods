package se.metacraft.portable_jukebox.compat;

import se.metacraft.lib.compat.IsLoaded;

public class CompatInit {

	public static void init() {
		if (IsLoaded.LEDGER.isLoaded()) {
			LedgerCompat.init();
		}
	}

}

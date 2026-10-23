package se.metacraft.zones.compat;

import se.metacraft.lib.compat.IsLoaded;
import se.metacraft.zones.compat.resource_pack.ResourcePackDataType;

public class CustomZoneDatas {

	public static void init() {
		if (IsLoaded.RESOURCE_PACKS.isLoaded()) {
			ResourcePackDataType.init();
		}
		if (IsLoaded.CORE.isLoaded()) {
			CoreTypes.init();
		}
	}

}

package nu.metacraft.zones.compat;

import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.zones.compat.resource_pack.ResourcePackDataType;

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

package se.datasektionen.mc.zones.compat;

import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.zones.compat.resource_pack.ResourcePackDataType;

public class CustomZoneDatas {

	public static void init() {
		if (IsLoaded.RESOURCE_PACKS.isLoaded()) {
			ResourcePackDataType.init();
		}
	}

}

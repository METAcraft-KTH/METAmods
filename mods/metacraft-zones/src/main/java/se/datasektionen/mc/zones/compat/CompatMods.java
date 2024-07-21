package se.datasektionen.mc.zones.compat;

import net.fabricmc.loader.api.FabricLoader;
import se.datasektionen.mc.zones.compat.leukocyte.LeukocyteZoneManager;

public enum CompatMods {
	LEUKOCYTE("leukocyte");
	final String modid;

	CompatMods(String modid) {
		this.modid = modid;
	}

	public boolean installed() {
		return FabricLoader.getInstance().isModLoaded(modid);
	}

	public static void init() {
		if (LEUKOCYTE.installed()) {
			LeukocyteZoneManager.init();
		}
	}

}

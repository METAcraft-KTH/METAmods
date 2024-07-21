package se.datasektionen.mc.metacraft_moderation.compat;

import net.fabricmc.loader.api.FabricLoader;

public enum CompatMods {
	METACRAFT_ZONES("metacraft-zones"),
	VANISH("melius-vanish");

	private final String modid;

	CompatMods(String modid) {
		this.modid = modid;
	}

	public boolean isInstalled() {
		return FabricLoader.getInstance().isModLoaded(modid);
	}

}

package se.datasektionen.mc.metacraft_lib.compat;

import net.fabricmc.loader.api.FabricLoader;

public record IsLoaded(String modID) {

	public static final IsLoaded CARPET = new IsLoaded("carpet");
	public static final IsLoaded LEUKOCYTE = new IsLoaded("leukocyte");
	public static final IsLoaded METACRAFT_ZONES = new IsLoaded("metacraft-zones");
	public static final IsLoaded METACRAFT_LOOT_CONTAINERS = new IsLoaded("metacraft-loot-containers");
	public static final IsLoaded VANISH = new IsLoaded("melius-vanish");

	public boolean isLoaded() {
		return FabricLoader.getInstance().isModLoaded(modID);
	}

	public void ifLoaded(Runnable code) {
		if (isLoaded()) {
			code.run();
		}
	}

	public void ifLoadedOrElse(Runnable ifLoaded, Runnable otherwise) {
		if (isLoaded()) {
			ifLoaded.run();
		} else {
			otherwise.run();
		}
	}

}

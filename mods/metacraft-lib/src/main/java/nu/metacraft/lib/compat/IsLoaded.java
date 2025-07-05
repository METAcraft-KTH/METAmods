package nu.metacraft.lib.compat;

import net.fabricmc.loader.api.FabricLoader;

@SuppressWarnings("unused")
public record IsLoaded(String modID) {

	public static final IsLoaded CARPET = new IsLoaded("carpet");
	public static final IsLoaded LEUKOCYTE = new IsLoaded("leukocyte");
	public static final IsLoaded METACRAFT_ZONES = new IsLoaded("metacraft-zones");
	public static final IsLoaded METACRAFT_LOOT_CONTAINERS = new IsLoaded("metacraft-loot-containers");
	public static final IsLoaded VANISH = new IsLoaded("melius-vanish");
	public static final IsLoaded SQUAREMAP = new IsLoaded("squaremap");
	public static final IsLoaded PORTAL_BLOCKER = new IsLoaded("portal-blocker");
	public static final IsLoaded RESOURCE_PACKS = new IsLoaded("metacraft-resource-packs");
	public static final IsLoaded CORE = new IsLoaded("metacraft-core");
	public static final IsLoaded DISCORD_MC_CHAT = new IsLoaded("discord-mc-chat");

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

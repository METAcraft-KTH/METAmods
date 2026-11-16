package se.metacraft.ovvarclient;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Patches the instance's shaderpacks before the game (and Iris) starts. */
public class PreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		try {
			StartupPatch.run(FabricLoader.getInstance().getGameDir());
		} catch (Throwable t) {
			// StartupPatch.run does not throw; this is belt and braces. Startup never fails because of this mod.
			System.err.println("[ovvar-client] " + t);
		}
	}
}

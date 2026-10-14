package nu.metacraft.nether_rifts;

import net.minecraft.server.MinecraftServer;
import nu.metacraft.nether_rifts.rift.RiftData;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

/** Turning rift mode off (the realm steadies) and back on (for testing and staging). */
public final class Stabilisation {

	private Stabilisation() {}

	public static void stabilise(MinecraftServer server) {
		RiftWorldState.get(server).setRiftMode(false);
		setNetherLocked(server, false);
		for (var level : server.getAllLevels()) {
			var data = RiftData.get(level);
			for (var rift : data.all()) {
				if (!rift.stage().raiding() && !rift.crying()) {
					rift.becomePortal(level, data);
				}
			}
		}
	}

	public static void destabilise(MinecraftServer server) {
		RiftWorldState.get(server).setRiftMode(true);
		setNetherLocked(server, true);
	}

	/** Lighting and travel only; generation and the End stay as staff set them. */
	private static void setNetherLocked(MinecraftServer server, boolean locked) {
		var blocker = PortalBlockerSettings.getInstance(server);
		blocker.setPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.ACTIVATION, locked);
		blocker.setPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL, locked);
	}
}

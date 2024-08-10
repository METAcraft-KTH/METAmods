package se.datasektionen.mc.portal_blocker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;
import se.datasektionen.mc.portal_blocker.zone.ZoneDataPortalBlocker;

public class PortalBlocker implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("Portal-Blocker");

	public static final String MODID = "portal-blocker";

	@Override
	public void onInitialize() {
		ZoneDataPortalBlocker.init();
		PortalTypeRegistry.init();
		Commands.registerCommands();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server instanceof DedicatedServer dedicatedServer) {
				var instance = PortalBlockerSettings.getInstance(server);
				boolean netherBlocked = PortalBlockerSettings.getInstance(server).isPortalBlockedGlobally(
						PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL
				);
				if (dedicatedServer.getProperties().allowNether == netherBlocked) {
					instance.setPortalBlockedGlobally(
							PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL,
							!dedicatedServer.getProperties().allowNether
					);
					LOGGER.info(
							"server.properties mismatch, changing nether portals from " + Commands.getBlockStateText(netherBlocked) +
							" to " + Commands.getBlockStateText(!dedicatedServer.getProperties().allowNether)
					);
				}
			}
		});
		LOGGER.info("Loaded Portal-Blocker by Leddy231 and Acuadragon100");
	}

	public static Identifier getID(String name) {
		return Identifier.of(MODID, name);
	}
}

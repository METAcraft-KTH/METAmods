package nu.metacraft.portal_blocker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import nu.metacraft.portal_blocker.zone.ZoneDataPortalBlocker;

public class PortalBlocker implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("Portal-Blocker");

	public static final String MODID = "portal-blocker";

	@Override
	public void onInitialize() {
		ZoneDataPortalBlocker.init();
		PortalTypeRegistry.init();
		Commands.registerCommands();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			var instance = PortalBlockerSettings.getInstance(server);
			boolean netherBlocked = PortalBlockerSettings.getInstance(server).isPortalBlockedGlobally(
					PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL
			);
			if (server.getGameRules().getBoolean(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS) == netherBlocked) {
				instance.setPortalBlockedGlobally(
						PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL,
						!netherBlocked
				);
			}
		});
		LOGGER.info("Loaded Portal-Blocker by Leddy231 and Acuadragon100");
	}

	public static Identifier getID(String name) {
		return Identifier.of(MODID, name);
	}
}

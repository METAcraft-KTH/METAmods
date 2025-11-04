package nu.metacraft.portal_blocker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
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
			if (server.getGameRules().getBoolean(GameRules.RULE_ALLOW_NETHER) == netherBlocked) {
				instance.setPortalBlockedGlobally(
						PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL,
						!netherBlocked
				);
			}
		});
		LOGGER.info("Loaded Portal-Blocker by Leddy231 and Acuadragon100");
	}

	public static ResourceLocation getID(String name) {
		return ResourceLocation.fromNamespaceAndPath(MODID, name);
	}
}

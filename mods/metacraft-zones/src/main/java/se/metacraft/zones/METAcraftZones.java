package se.metacraft.zones;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.lib.compat.IsLoaded;
import se.metacraft.zones.compat.CustomZoneDatas;
import se.metacraft.zones.compat.leukocyte.LeukocyteZoneManager;
import se.metacraft.zones.zone.RealZone;
import se.metacraft.zones.zone.ZoneRegistry;
import se.metacraft.zones.zone.data.ZoneDataRegistry;

public class METAcraftZones implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("METAcraft-Zones");

	public static final String MODID = "metacraft-zones";

	@Override
	public void onInitialize() {
		ZoneRegistry.init();
		ZoneDataRegistry.init();
		ZoneManagementCommand.init();
		Commands.registerCommands();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ZoneManager.getInstance(server).getZones().getZones().forEach(RealZone::onWorldLoad);
		});
		if (IsLoaded.LEUKOCYTE.isLoaded()) {
			LeukocyteZoneManager.init();
		}
		CustomZoneDatas.init();
		LOGGER.info("Loaded METAcraft zones by Acuadragon100");
	}

	public static Identifier getID(String name) {
		return Identifier.fromNamespaceAndPath(MODID, name);
	}
}

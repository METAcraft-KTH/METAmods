package se.datasektionen.mc.zones;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.zones.compat.CompatMods;
import se.datasektionen.mc.zones.zone.RealZone;
import se.datasektionen.mc.zones.zone.ZoneRegistry;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;

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
		CompatMods.init();
		LOGGER.info("Loaded METAcraft zones by Acuadragon100");
	}

	public static Identifier getID(String name) {
		return Identifier.of(MODID, name);
	}
}

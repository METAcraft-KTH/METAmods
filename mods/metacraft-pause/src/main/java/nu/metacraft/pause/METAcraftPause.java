package nu.metacraft.pause;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.METAcraftLib;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftPause implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("METAcraft-Pause");

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final String MODID = "metacraft-pause";

	@Override
	public void onInitialize() {
		Commands.init();
		PauseEvents.init();
	}

	public static Identifier getID(String name) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, name);
	}
}

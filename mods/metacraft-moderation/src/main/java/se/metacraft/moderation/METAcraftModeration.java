package se.metacraft.moderation;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.lib.compat.IsLoaded;
import se.metacraft.moderation.exile.ExileInit;

public class METAcraftModeration implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("METAcraft-Moderation");

	public static final String MODID = "metacraft-moderation";

	public static final boolean ENABLE_EXILE = IsLoaded.METACRAFT_ZONES.isLoaded();

	@Override
	public void onInitialize() {
		if (ENABLE_EXILE) {
			ExileInit.init();
		}
		Commands.init();
	}

	public static Identifier getID(String name) {
		return Identifier.fromNamespaceAndPath(MODID, name);
	}
}

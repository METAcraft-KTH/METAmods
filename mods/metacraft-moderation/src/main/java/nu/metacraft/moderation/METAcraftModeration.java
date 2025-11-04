package nu.metacraft.moderation;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.moderation.exile.ExileInit;

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

	public static ResourceLocation getID(String name) {
		return ResourceLocation.fromNamespaceAndPath(MODID, name);
	}
}

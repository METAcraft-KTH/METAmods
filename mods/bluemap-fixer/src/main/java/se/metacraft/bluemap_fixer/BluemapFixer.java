package se.metacraft.bluemap_fixer;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.lib.compat.IsLoaded;

public class BluemapFixer implements ModInitializer {

	public static final String MODID = "bluemap-fixer";

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		if (IsLoaded.BLUEMAP.isLoaded()) {
			BluemapCompat.init();
		}
	}
}

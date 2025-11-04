package nu.metacraft.resource_packs;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResourcePacks implements ModInitializer {
	public static final String NAMESPACE = "metacraft";

	public static final String MODID = "metacraft-resource-packs";

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		ResourcePackServerManager.init();
		Commands.init();
		var f = ResourcePackConfig.RESOURCE_PACK_DIR.toFile();
		if (!f.exists()) {
			f.mkdirs();
		}
	}

	public static ResourceLocation getID(String id) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
	}
}

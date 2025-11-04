package nu.metacraft.bundles;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import nu.metacraft.bundles.util.BundleHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftBundles implements ModInitializer {

	public static final String NAMESPACE = "metacraft";
	public static final Logger LOGGER = LogManager.getLogger("metacraft-bundles");

	@Override
	public void onInitialize() {
		BundleComponents.init();
		BundleHelper.init();
	}

	public static ResourceLocation getID(String id) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
	}
}

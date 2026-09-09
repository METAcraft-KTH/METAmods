package nu.metacraft.bundles;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.bundles.util.BundleHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftBundles implements ModInitializer {

	public static final String MODID = "metacraft-bundles";
	public static final String NAMESPACE = "metacraft";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		BundleComponents.init();
		BundleHelper.init();

		if (BundleConfig.getInstance().bundleRendering()) {
			PolymerResourcePackUtils.addModAssets(MODID);
			PolymerResourcePackUtils.markAsRequired();
		}
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}

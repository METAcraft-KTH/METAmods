package nu.metacraft.revival;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import nu.metacraft.lib.METAcraftLib;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftRevival implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("metacraft-revival");

	@Override
	public void onInitialize() {
		RevivalDialogs.init();
		RevivalEvents.init();
	}

	public static ResourceLocation getID(String name) {
		return ResourceLocation.fromNamespaceAndPath(METAcraftLib.NAMESPACE, name);
	}

}

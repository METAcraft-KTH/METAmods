package se.datasektionen.mc.simplecustomfeatures;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;

public class Features implements ModInitializer {

	public static final String MODID = "simple_custom_features";

	public static final Logger LOGGER = LogManager.getLogger("simple-custom-features");

	@Override
	public void onInitialize() {
		ObjectRegistry.init();
		Events.init();
	}

	public static Identifier getID(String id) {
		return Identifier.of(MODID, id);
	}

}

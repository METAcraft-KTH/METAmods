package se.datasektionen.mc.simplecustomfeatures;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.simplecustomfeatures.compat.PortalBlockerCompat;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.items.simple.ArmorMaterialRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.items.simple.ToolMaterialRegistry;

public class Features implements ModInitializer, ClientModInitializer, DedicatedServerModInitializer {

	public static final String MODID = "simple_custom_features";

	public static final Logger LOGGER = LogManager.getLogger("simple-custom-features");

	@Override
	public void onInitialize() {
		ArmorMaterialRegistry.init();
		ToolMaterialRegistry.init();
		ObjectRegistry.init();
		Events.init();

		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			PortalBlockerCompat.init();
		}
	}

	@Override
	public void onInitializeClient() {
		post();
	}

	@Override
	public void onInitializeServer() {
		post();
	}

	private void post() {
		FeaturesConfig.getConfig(); //Init config, to allow datapacks to access all non-registry-dependent items.
	}

	public static Identifier getID(String id) {
		return Identifier.of(MODID, id);
	}

}

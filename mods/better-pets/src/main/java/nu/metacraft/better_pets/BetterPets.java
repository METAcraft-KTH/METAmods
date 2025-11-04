package nu.metacraft.better_pets;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BetterPets implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("Portal-Blocker");

	public static final String MODID = "better-pets";
	public static final String NAMESPACE = "better_pets";

	@Override
	public void onInitialize() {

	}

	public static ResourceLocation getID(String name) {
		return ResourceLocation.fromNamespaceAndPath(NAMESPACE, name);
	}
}

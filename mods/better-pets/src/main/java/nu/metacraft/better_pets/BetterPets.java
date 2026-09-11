package nu.metacraft.better_pets;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BetterPets implements ModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("Portal-Blocker");

	public static final String MODID = "better-pets";
	public static final String NAMESPACE = "better_pets";

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String name) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, name);
	}
}

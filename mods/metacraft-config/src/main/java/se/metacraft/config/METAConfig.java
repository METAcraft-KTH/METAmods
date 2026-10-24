package se.metacraft.config;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import se.metacraft.lib.METAcraftLib;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAConfig implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("metacraft-config");
	public static final String NAMESPACE = METAcraftLib.NAMESPACE;

	@Override
	public void onInitialize() {

	}

	public static Identifier getID(String value) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, value);
	}
}

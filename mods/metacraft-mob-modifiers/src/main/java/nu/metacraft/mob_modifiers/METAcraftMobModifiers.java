package nu.metacraft.mob_modifiers;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.METAcraftLib;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftMobModifiers implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("metacraft-mob-modifiers");

	public static Identifier getID(String id) {
		return METAcraftLib.getID(id);
	}

	@Override
	public void onInitialize() {

	}
}

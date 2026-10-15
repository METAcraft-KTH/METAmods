package nu.metacraft.revival;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.revival.predicate.METAcraftRevivalPredicates;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class METAcraftRevival implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("metacraft-revival");

	@Override
	public void onInitialize() {
		RevivalDialogs.init();
		RevivalEvents.init();
		METAcraftRevivalPredicates.init();
	}

	public static Identifier getID(String name) {
		return Identifier.fromNamespaceAndPath(METAcraftLib.NAMESPACE, name);
	}

}

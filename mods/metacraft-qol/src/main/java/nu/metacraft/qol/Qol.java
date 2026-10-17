package nu.metacraft.qol;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.qol.silence_mobs.SilenceMobs;
import nu.metacraft.qol.void_anchor.VoidAnchor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Small quality-of-life features for Metacraft, each in its own package and its own section of the config. */
public class Qol implements ModInitializer {

	public static final String NAMESPACE = METAcraftLib.NAMESPACE;
	public static final String MODID = "metacraft-qol";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		VoidAnchor.init();
		SilenceMobs.init();

		PolymerResourcePackUtils.addModAssets(MODID);
		PolymerResourcePackUtils.markAsRequired();
	}

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, id);
	}
}

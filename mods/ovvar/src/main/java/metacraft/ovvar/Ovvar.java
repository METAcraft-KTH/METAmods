package metacraft.ovvar;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveFeet;
import metacraft.ovvar.content.OvveTop;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.pack.Combos;
import metacraft.ovvar.pack.WardrobeArt;
import metacraft.ovvar.sewing.StandDisplays;
import metacraft.ovvar.sewing.StandSewing;
import metacraft.ovvar.sewing.WardrobeMannequin;
import metacraft.ovvar.sewing.StashSession;
import metacraft.ovvar.store.Wardrobes;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ovvar: student overalls with sewn-on patches, for vanilla clients via Polymer. Everything the
 * client draws is a pre-generated equipment asset, so the pack is required — without it a garment
 * is a leather piece wearing the wrong skin, which is exactly the kind of quiet wrongness we refuse.
 */
public class Ovvar implements ModInitializer {
	public static final String MOD_ID = "ovvar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/**
	 * Is this the datagen run rather than a server? Anything that reads a generated asset has to
	 * keep out of the run that writes it — {@link metacraft.ovvar.content.ModContent} checks the
	 * same property before demanding the assets exist.
	 */
	public static final boolean DATAGEN = System.getProperty("fabric-api.datagen") != null;

	@Override
	public void onInitialize() {
		OvvarConfig.get();
		ModContent.register();
		OvveTop.init();
		OvveFeet.init();
		StandSewing.init();
		StandDisplays.init();
		Combos.init();
		WardrobeArt.init();
		Wardrobes.init();
		StashSession.init();
		WardrobeMannequin.init();
		ModCommands.init();
		Motd.init();

		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();

		LOGGER.info("[{}] {} chapter(s), {} patch(es)", MOD_ID, Chapter.values().length, Patches.all().size());
	}
}

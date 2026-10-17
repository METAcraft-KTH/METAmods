package metacraft.kultur;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.kultur.catalogue.Catalogue;
import metacraft.kultur.content.ModContent;
import metacraft.kultur.drinks.KulturDrinks;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kultur: chapter culture for vanilla clients via Polymer — banner and shield patterns, paintings
 * and the chapter drinks. Everything the client sees is in the data files and the pack, so the
 * mod's own job is small: read the catalogue, register the pattern items, hand Polymer the assets,
 * and, when Brewery is not on the server, brew the drinks itself ({@link KulturDrinks}).
 */
public class Kultur implements ModInitializer {
	public static final String MOD_ID = "metacraft-kultur";
	/** The resource and registry namespace ({@code kultur:itk}, {@code assets/kultur}); not the mod id. */
	public static final String NAMESPACE = "kultur";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static Catalogue catalogue;

	@Override
	public void onInitialize() {
		catalogue = Catalogue.load();
		ModContent.register(catalogue);
		KulturDrinks.register();
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
		LOGGER.info("[{}] {} chapter(s), {} pattern(s) ({} with items), {} painting(s)", MOD_ID,
				catalogue.chapters().size(), catalogue.patterns().size(), ModContent.items().size(), catalogue.paintings().size());
	}

	/** The catalogue the running server was built from. */
	public static Catalogue catalogue() {
		if (catalogue == null) catalogue = Catalogue.load();
		return catalogue;
	}
}

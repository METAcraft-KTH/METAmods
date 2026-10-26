package se.metacraft.kultur;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import se.metacraft.kultur.catalogue.Catalogue;
import se.metacraft.kultur.content.ModContent;
import se.metacraft.kultur.drinks.KulturDrinks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
	/**
	 * The recipes of the drinks Kultur brews without Brewery, as a built-in datapack that is off until
	 * somebody turns it on ({@code /datapack enable "kultur:vanilla_drinks"}): Season 6 brings the
	 * drinks at an event, not from day one. The drinks themselves, their effects and drunkenness are
	 * there either way; only making them waits. {@code -Dkultur.vanillaDrinks=true} turns it on from
	 * the start, which the dev runs do so the drink tests have their recipes.
	 */
	public static final Identifier VANILLA_DRINKS = Identifier.fromNamespaceAndPath(NAMESPACE, "vanilla_drinks");
	private static Catalogue catalogue;

	@Override
	public void onInitialize() {
		catalogue = Catalogue.load();
		ModContent.register(catalogue);
		KulturDrinks.register();
		if (KulturDrinks.active()) {
			boolean on = Boolean.getBoolean("kultur.vanillaDrinks");
			ResourceLoader.registerBuiltinPack(VANILLA_DRINKS, FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(),
					Component.literal("Kultur: chapter drinks without Brewery"), on ? PackActivationType.DEFAULT_ENABLED : PackActivationType.NORMAL);
			LOGGER.info("[{}] chapter drink recipes (without Brewery): {}", MOD_ID, on ? "on" : "off until /datapack enable \"" + VANILLA_DRINKS + "\"");
		}
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

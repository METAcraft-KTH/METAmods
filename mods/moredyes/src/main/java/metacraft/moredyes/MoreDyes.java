package metacraft.moredyes;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.moredyes.banner.BannerPatterns;
import metacraft.moredyes.color.ModColors;
import metacraft.moredyes.content.ModContent;
import metacraft.moredyes.recipe.ModRecipes;
import metacraft.moredyes.sheep.SheepColors;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * More Dyes: server-side dye colours for vanilla clients, via Polymer.
 *
 * Order matters: colours first (everything is derived from them), then content (which asks Polymer
 * for donor states and fails the startup if a pool is short), then the resource pack, which must be
 * required because a client without it has nothing meaningful to show.
 */
public class MoreDyes implements ModInitializer {
	public static final String MOD_ID = "moredyes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModColors.load();
		ModContent.register();
		ModRecipes.init();
		SheepColors.init();
		ModCommands.init();
		BannerPatterns.init();

		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();

		LOGGER.info("[{}] registered {} colour(s)", MOD_ID, ModColors.all().size());
	}
}

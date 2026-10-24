package se.metacraft.moredyes;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import se.metacraft.moredyes.armor.AnimatedArmor;
import se.metacraft.moredyes.banner.BannerPatterns;
import se.metacraft.moredyes.color.ModColors;
import se.metacraft.moredyes.content.ModContent;
import se.metacraft.moredyes.pet.Collars;
import se.metacraft.moredyes.recipe.ModRecipes;
import se.metacraft.moredyes.sheep.SheepColors;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.metacraft.moredyes.content.block.ModFlower;
import se.metacraft.moredyes.cushion.Cushions;

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
		AnimatedArmor.init();
		SheepColors.init();
		Collars.init();
		ModFlower.addToSnifferDigging();
		Cushions.init();
		ModCommands.init();
		BannerPatterns.init();

		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();

		LOGGER.info("[{}] registered {} colour(s)", MOD_ID, ModColors.all().size());
	}
}

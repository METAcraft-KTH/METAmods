package nu.metacraft.booklet.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.brewery.BreweryInit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import nu.metacraft.booklet.MetacraftBooklet;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The Brewing chapter's recipe pages are written from the drinks by {@code tools/brewing_recipes.py};
 * this keeps them from going stale: every drink the running Brewery knows, its own and kultur's, has
 * its header on one of them.
 */
public final class BrewingTests {
	private static final List<String> RECIPE_PAGES = List.of(
			"resourcepacks/brewing/data/metacraft/booklet/pages/en_us/brewing/drinks.txt",
			"resourcepacks/brewing_kultur/data/metacraft/booklet/pages/en_us/brewing/chapter_drinks.txt");

	@GameTest
	public void everyDrinkHasARecipe(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded("brewery")) {
			helper.succeed();   // no Brewery, no chapter
			return;
		}
		WithBrewery.everyDrinkHasARecipe(helper);
	}

	/** Names a Brewery class, so it is loaded only once Brewery is known to be there. */
	private static final class WithBrewery {
		static void everyDrinkHasARecipe(GameTestHelper helper) {
			var self = FabricLoader.getInstance().getModContainer(MetacraftBooklet.MOD_ID).orElseThrow();
			StringBuilder text = new StringBuilder();
			for (String page : RECIPE_PAGES) {
				self.findPath(page).ifPresent(path -> {
					try {
						text.append(Files.readString(path, StandardCharsets.UTF_8));
					} catch (IOException e) {
						helper.fail("cannot read " + page + ": " + e);
					}
				});
			}
			JsonObject lang;
			try (var in = BreweryInit.class.getResourceAsStream("/assets/brewery/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (IOException | NullPointerException e) {
				helper.fail("cannot read Brewery's en_us names: " + e);
				return;
			}
			List<String> missing = new ArrayList<>();
			BreweryInit.DRINK_TYPES.forEach((id, type) -> {
				if (id.getPath().equals("the_testificate")) return;   // Brewery's own, in a dev environment only
				var name = type.looks().nameSelector().select(7).text();
				String english = name.getContents() instanceof TranslatableContents t
						? (lang.has(t.getKey()) ? lang.get(t.getKey()).getAsString() : t.getFallback())
						: name.getString();
				if (english == null || !text.toString().contains("### Header: " + english + "\n")) missing.add(id + " (" + english + ")");
			});
			if (!missing.isEmpty()) helper.fail("no recipe in the Brewing chapter for " + String.join(", ", missing)
					+ "; run mods/metacraft-booklet/tools/brewing_recipes.py");
			helper.succeed();
		}
	}
}

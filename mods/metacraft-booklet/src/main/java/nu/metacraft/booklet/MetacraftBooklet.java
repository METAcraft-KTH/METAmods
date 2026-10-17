package nu.metacraft.booklet;

import eu.pb4.booklet.impl.BookletImplUtil;
import eu.pb4.booklet.impl.BookletOpenState;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import nu.metacraft.lib.CreativeTabNamespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The server's guidebook. <a href="https://github.com/Patbox/booklet">Booklet</a> draws the book
 * (a dialog on a vanilla client); this mod supplies the pages, in four sections: Student Culture
 * Features, Vanilla+ Features, Nether Changes and End Changes. {@code /guide} opens Booklet's index,
 * {@code booklet:index}, which the always-on {@code welcome} pack replaces with "Welcome to METAcraft":
 * the server's idea, then the sections, every page in {@code booklet:main_page}.
 *
 * <p>Each mod's pages are a built-in datapack under {@code resourcepacks/<name>/} of this jar,
 * registered only when its mod is loaded ({@link #HOOKS}), so a server without ovvar has no ovvar pages
 * and none of their item icons to resolve. A section's own page is in a pack that is on when any of its
 * mods is, so it lists only the pages that are there and is gone when none of them is. No chapter's mod
 * is a dependency.
 *
 * <p>Besides pages: {@code polydecorations_s6} switches off every PolyDecorations recipe but those
 * of the few things METAcraft keeps, and the resource pack turns the warning button vanilla puts on every server
 * dialog into a question mark.
 */
public final class MetacraftBooklet implements ModInitializer {
	public static final String MOD_ID = "metacraft-booklet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Booklet's index page, which {@code /guide} opens; the {@code welcome} pack replaces Booklet's own. */
	public static final Identifier INDEX = Identifier.fromNamespaceAndPath("booklet", "index");

	/**
	 * A built-in datapack switched on when its mods are loaded: every one of {@code mods}, or with
	 * {@code any} at least one of them; with no mods, always. {@code optional} packs are enabled by
	 * default but can be turned off with {@code /datapack disable}; chapters cannot.
	 */
	public record Hook(List<String> mods, boolean any, String pack, boolean optional) {
		public Hook(String modId, String pack, boolean optional) {
			this(List.of(modId), false, pack, optional);
		}

		public Hook(List<String> mods, String pack, boolean optional) {
			this(mods, false, pack, optional);
		}

		/** A pack on when any one of {@code mods} is loaded: a section's own page. */
		public static Hook anyOf(String pack, String... mods) {
			return new Hook(List.of(mods), true, pack, false);
		}

		/** A pack that is always on. */
		public static Hook always(String pack) {
			return new Hook(List.of(), false, pack, false);
		}

		public Identifier id() {
			return Identifier.fromNamespaceAndPath(MOD_ID, pack);
		}

		public boolean loaded() {
			if (mods.isEmpty()) return true;
			var loader = FabricLoader.getInstance();
			return any ? mods.stream().anyMatch(loader::isModLoaded) : mods.stream().allMatch(loader::isModLoaded);
		}

		/** Which mods switch it on, for the log. */
		public String describe() {
			return mods.isEmpty() ? "always" : String.join(any ? " or " : " and ", mods);
		}
	}

	/**
	 * Every hook. Where a page sits is its own {@code category=} and {@code order=}: the sections are
	 * in {@code booklet:main_page}, and each page in its section's category. A new mod's pages are a
	 * folder under {@code resourcepacks/} and a line here; if it belongs to a section, add the mod to
	 * that section's {@link Hook#anyOf} too.
	 */
	public static final List<Hook> HOOKS = List.of(
			// "Welcome to METAcraft": replaces Booklet's index, so /guide opens on it
			Hook.always("welcome"),

			// Student Culture Features (order: Chapter Drinks 10, Banner Patterns 20, Ovvar 40, Colors 50).
			// 30 is kept for Pirkkos, Ripsquid's pirkko mod: its chapter goes in a pack of its own with
			// category=metacraft:student_culture and order=30, a Hook here, and its mod id in this anyOf.
			Hook.anyOf("student_culture", "ovvar", "brewery", "metacraft-kultur", "moredyes"),
			new Hook("ovvar", "ovvar", false),
			// Brewery's own book, rewritten as a chapter (its recipe is switched off here); the
			// recipe pages are written by tools/brewing_recipes.py.
			new Hook("brewery", "brewing", false),
			new Hook(List.of("metacraft-kultur", "brewery"), "brewing_kultur", false),
			new Hook("metacraft-kultur", "kultur", false),
			new Hook("moredyes", "moredyes", false),

			// Vanilla+ Features
			Hook.anyOf("vanilla_plus", "metacraft-qol", "polydecorations"),
			// also End Changes: the void anchor, in both sections
			new Hook("metacraft-qol", "qol", false),
			new Hook("polydecorations", "decorating", false),
			new Hook("polydecorations", "polydecorations_s6", true),

			// Nether Changes
			new Hook("nether-rifts", "rifts", false));

	@Override
	public void onInitialize() {
		// Booklet's guidebook ("The Encyclopedia") has no tab of its own: list it in the METAcraft tab.
		CreativeTabNamespaces.add("booklet");
		ModContainer self = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
		for (Hook hook : HOOKS) {
			if (!hook.loaded()) continue;
			boolean ok = ResourceLoader.registerBuiltinPack(hook.id(), self,
					Component.literal("METAcraft Booklet: " + hook.pack()),
					hook.optional() ? PackActivationType.DEFAULT_ENABLED : PackActivationType.ALWAYS_ENABLED);
			LOGGER.info("[{}] {}: {} {}", MOD_ID, hook.describe(), hook.pack(), ok ? "on" : "could not be registered");
		}
		if (FabricLoader.getInstance().isModLoaded("polydecorations")) {
			// its feature switches, as a METAcraft config (/meta-config-screen polydecorations)
			nu.metacraft.booklet.polydecorations.PolyDecorationsConfig.init();
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("guide").executes(ctx -> {
					boolean opened = BookletImplUtil.openPage(ctx.getSource().getPlayerOrException(), INDEX, BookletOpenState.DEFAULT);
					return opened ? 1 : 0;
				})));

		Beside.init();

		// The question-mark sprites under assets/minecraft, and Booklet's own assets need the pack too.
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
	}
}

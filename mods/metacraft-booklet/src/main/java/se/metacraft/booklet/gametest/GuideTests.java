package se.metacraft.booklet.gametest;

import eu.pb4.booklet.api.body.AlignedItemBody;
import eu.pb4.booklet.api.body.AlignedMessage;
import eu.pb4.booklet.api.body.HeaderMessage;
import eu.pb4.booklet.api.body.ImageBody;
import eu.pb4.booklet.impl.BookletInit;
import eu.pb4.booklet.impl.BookletOpenState;
import eu.pb4.booklet.impl.BookletPage;
import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import se.metacraft.booklet.Beside;
import se.metacraft.booklet.MetacraftBooklet;
import se.metacraft.booklet.polydecorations.PolyDecorationsConfig;
import se.metacraft.booklet.polydecorations.S6Defaults;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Booklet reads pages leniently: a page that fails to parse is a stack trace in the log, a link to
 * a page that is not there is a button that does nothing, an unknown item id silently a stone. So
 * every chapter this mod registers is checked here against the running server's Booklet — for the
 * hooks whose mod is loaded, which in the dev runtime is all of them.
 */
public final class GuideTests {
	private static final String LANG = "en_us";
	private static final Pattern PAGE_LINK = Pattern.compile("<page(?:link|ref)\\s+'([^']+)'");
	private static final Pattern ITEM = Pattern.compile("<c?item\\s+'([^']+)'");
	private static final Pattern IMAGE = Pattern.compile("(?m)^### Image:\\s*(\\S+)");
	private static final Pattern CATEGORY = Pattern.compile("(?m)^category=(.*)$");
	private static final Pattern ENTRIES = Pattern.compile("(?m)^### Category Entries:\\s*(\\S+)");
	private static final Identifier BOOKSHELF = Identifier.fromNamespaceAndPath("booklet", "main_page");
	/**
	 * Pages other mods open by id, from a command or a chat link. Booklet ignores a page that isn't
	 * there, so a renamed page would leave their link a button that does nothing.
	 */
	private static final Map<String, Identifier> OPENED_BY = Map.of(
			"ovvar", Identifier.fromNamespaceAndPath("metacraft", "ovvar"),
			"nether-rifts", Identifier.fromNamespaceAndPath("metacraft", "rifts"));

	/** A page file of a loaded hook's pack: its text, and the hook that brings it. */
	private record Page(String text, MetacraftBooklet.Hook hook) {}

	/** Every page file in the loaded hooks' packs, by page id. */
	private static Map<Identifier, Page> pages(GameTestHelper helper) {
		Map<Identifier, Page> out = new LinkedHashMap<>();
		var self = FabricLoader.getInstance().getModContainer(MetacraftBooklet.MOD_ID).orElseThrow();
		for (MetacraftBooklet.Hook hook : MetacraftBooklet.HOOKS) {
			if (!hook.loaded()) continue;
			var data = self.findPath("resourcepacks/" + hook.pack() + "/data");
			if (data.isEmpty()) {
				helper.fail("hook " + hook.pack() + " has no resourcepacks/" + hook.pack() + "/data folder");
				continue;
			}
			try (Stream<Path> files = Files.walk(data.get())) {
				for (Path file : files.filter(Files::isRegularFile).toList()) {
					Path rel = data.get().relativize(file);
					// <namespace>/booklet/pages/<lang>/<path>.txt
					if (rel.getNameCount() < 5 || !rel.getName(1).toString().equals("booklet") || !rel.getName(3).toString().equals(LANG)) continue;
					String path = rel.subpath(4, rel.getNameCount()).toString().replace('\\', '/');
					out.put(Identifier.fromNamespaceAndPath(rel.getName(0).toString(), path.substring(0, path.length() - ".txt".length())),
							new Page(Files.readString(file, StandardCharsets.UTF_8), hook));
				}
			} catch (IOException e) {
				helper.fail("cannot read " + hook.pack() + ": " + e);
			}
		}
		if (out.isEmpty()) helper.fail("no guide pages found at all");
		return out;
	}

	@GameTest
	public void everyPageLoadsWithATitleADescriptionAndAnIcon(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Identifier id : pages(helper).keySet()) {
			Map<String, BookletPage> langs = BookletInit.PAGES.get(id);
			BookletPage page = langs == null ? null : langs.get(LANG);
			if (page == null) { wrong.add(id + " did not load"); continue; }
			if (page.info().title().getString().equals(id.toString())) wrong.add(id + " has no title=");
			if (page.info().description().isEmpty()) wrong.add(id + " has no description=");
			if (page.info().icon().isEmpty()) wrong.add(id + " has no icon= that parses");
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyPageIsReachableFromTheIndex(GameTestHelper helper) {
		// Every page is in a category, and every category it names is either the bookshelf or one a
		// page on the bookshelf lists — so nothing is written that no reader can get to.
		Map<Identifier, Page> pages = pages(helper);
		List<String> wrong = new ArrayList<>();
		for (var entry : pages.entrySet()) {
			if (entry.getKey().equals(MetacraftBooklet.INDEX)) continue;   // the welcome page is the index itself
			Matcher m = CATEGORY.matcher(entry.getValue().text());
			if (!m.find()) { wrong.add(entry.getKey() + " is in no category"); continue; }
			for (String raw : m.group(1).split(",")) {
				Identifier cat = Identifier.tryParse(raw.strip());
				if (cat == null) { wrong.add(entry.getKey() + " names category " + raw); continue; }
				if (!BookletInit.CATEGORIES.getOrDefault(cat, List.of()).contains(entry.getKey())) {
					wrong.add(entry.getKey() + " is not listed in " + cat);
				}
				if (!cat.equals(BOOKSHELF) && pages.values().stream().noneMatch(p -> p.text().contains("### Category Entries: " + cat))) {
					wrong.add(entry.getKey() + "'s category " + cat + " is listed by no page");
				}
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyLinkAndItemInTheGuideExists(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (var entry : pages(helper).entrySet()) {
			Matcher links = PAGE_LINK.matcher(entry.getValue().text());
			while (links.find()) {
				Identifier target = Identifier.tryParse(links.group(1));
				if (target == null || !BookletInit.PAGES.containsKey(target)) wrong.add(entry.getKey() + " links to " + links.group(1));
			}
			Matcher items = ITEM.matcher(entry.getValue().text());
			while (items.find()) {
				Identifier item = Identifier.tryParse(items.group(1));
				if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) wrong.add(entry.getKey() + " names item " + items.group(1));
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void theBookOpensOnTheWelcomePage(GameTestHelper helper) {
		// The welcome pack replaces Booklet's own index, so it has to be read after Booklet's jar.
		Map<String, BookletPage> langs = BookletInit.PAGES.get(MetacraftBooklet.INDEX);
		String title = langs == null || langs.get(LANG) == null ? null : langs.get(LANG).info().title().getString();
		if (!"Welcome to METAcraft".equals(title)) helper.fail("/guide opens " + MetacraftBooklet.INDEX + " titled " + title + ", not the welcome page");
		helper.succeed();
	}

	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("metacraft", "logo");
	private static final String INTRO = "You play METAcraft on a vanilla client";
	private static final String DISCORD = "https://discord.com/invite/MttUbkmeyD";

	/**
	 * {@code /guide} opens our welcome page, not the index in Booklet's jar: the server reads
	 * {@code booklet:index} from the welcome pack, and the page shows the METAcraft logo above the
	 * welcome text, then a "Join our Discord" link that opens the invite, then still lists every section
	 * on the bookshelf.
	 */
	@GameTest
	@SuppressWarnings("removal")   // the mock player, as ovvar's tests use it
	public void theWelcomePageShowsTheLogoAndTheDiscordLink(GameTestHelper helper) {
		var file = Identifier.fromNamespaceAndPath("booklet", "booklet/pages/" + LANG + "/index.txt");
		List<String> sources = helper.getLevel().getServer().getResourceManager().getResourceStack(file).stream()
				.map(Resource::sourcePackId).toList();
		String welcome = Identifier.fromNamespaceAndPath(MetacraftBooklet.MOD_ID, "welcome").toString();
		if (sources.isEmpty() || !sources.getLast().equals(welcome)) {
			helper.fail("booklet:index is read from " + (sources.isEmpty() ? "nowhere" : sources.getLast()) + ", not "
					+ welcome + " (the packs that have one, bottom to top: " + sources + ")");
		}

		BookletPage page = BookletInit.PAGES.getOrDefault(MetacraftBooklet.INDEX, Map.of()).get(LANG);
		if (page == null) {
			helper.fail("booklet:index did not load");
			return;
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ParserContext context = ParserContext.of(BookletOpenState.KEY, BookletOpenState.DEFAULT)
				.with(ServerPlaceholderContext.SERVER_KEY, ServerPlaceholderContext.of(player));
		List<DialogBody> body = page.body().stream().flatMap(part -> part.apply(context).stream()).toList();

		int logo = -1;
		int intro = -1;
		int discord = -1;
		for (int i = 0; i < body.size(); i++) {
			DialogBody part = body.get(i);
			if (logo < 0 && part instanceof ImageBody image && image.identifier().equals(LOGO)) logo = i;
			Component text = text(part);
			if (text == null) continue;
			if (intro < 0 && text.getString().contains(INTRO)) intro = i;
			if (discord < 0 && text.toFlatList().stream().anyMatch(bit -> bit.getString().contains("Join our Discord")
					&& bit.getStyle().getClickEvent() instanceof ClickEvent.OpenUrl url && url.uri().toString().equals(DISCORD))) {
				discord = i;
			}
		}
		if (logo < 0) helper.fail("the welcome page shows no " + LOGO + " image");
		if (intro < 0) helper.fail("the welcome page has no line starting \"" + INTRO + "\"");
		if (discord < 0) helper.fail("the welcome page has no \"Join our Discord\" that opens " + DISCORD);
		if (!(logo < intro && intro < discord)) helper.fail("the welcome page is not logo, welcome text, Discord link, in that order");

		// Every section on the bookshelf, by title, below the Discord link.
		String below = body.subList(discord + 1, body.size()).stream().map(GuideTests::text)
				.filter(Objects::nonNull).map(Component::getString).reduce("", (a, b) -> a + "\n" + b);
		List<Identifier> sections = BookletInit.CATEGORIES.getOrDefault(BOOKSHELF, List.of());
		if (sections.isEmpty()) helper.fail("no section is on the bookshelf");
		for (Identifier section : sections) {
			BookletPage sectionPage = BookletInit.PAGES.getOrDefault(section, Map.of()).get(LANG);
			String title = sectionPage == null ? section.toString() : sectionPage.info().getExternalTitle().getString();
			if (!below.contains(title)) helper.fail("the welcome page does not list " + section + " (" + title + ")");
		}
		helper.succeed();
	}

	/** The text a body part shows (an item's, its label), or null for one that is not text (an image). */
	private static Component text(DialogBody part) {
		return switch (part) {
			case PlainMessage m -> m.contents();
			case AlignedMessage m -> m.contents();
			case HeaderMessage m -> m.contents();
			case AlignedItemBody m -> m.description().contents();
			case ItemBody m -> m.description().map(PlainMessage::contents).orElse(null);
			default -> null;
		};
	}

	@GameTest
	public void everySectionListsAtLeastOnePage(GameTestHelper helper) {
		// A section's pack is on when any of its mods is; the pages it lists come with those mods.
		Map<Identifier, Page> pages = pages(helper);
		List<String> wrong = new ArrayList<>();
		for (Identifier section : BookletInit.CATEGORIES.getOrDefault(BOOKSHELF, List.of())) {
			Page page = pages.get(section);
			if (page == null) continue;   // another mod's chapter
			Matcher entries = ENTRIES.matcher(page.text());
			if (!entries.find()) { wrong.add(section + " lists no category"); continue; }
			Identifier cat = Identifier.tryParse(entries.group(1).strip());
			if (BookletInit.CATEGORIES.getOrDefault(cat, List.of()).isEmpty()) wrong.add(section + " is an empty section");
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * Whatever mix of mods a server has, every page it loads can still be reached: one of its categories
	 * is the bookshelf, or is listed by a page whose pack is on whenever this page's pack is. So a mod
	 * whose pages go in a section must also be in that section's {@code Hook.anyOf}.
	 */
	@GameTest
	public void everyPageStaysReachableWithModsMissing(GameTestHelper helper) {
		Map<Identifier, Page> pages = pages(helper);
		List<String> wrong = new ArrayList<>();
		for (var entry : pages.entrySet()) {
			if (entry.getKey().equals(MetacraftBooklet.INDEX)) continue;
			Matcher m = CATEGORY.matcher(entry.getValue().text());
			if (!m.find()) continue;   // everyPageIsReachableFromTheIndex says so
			boolean reachable = false;
			for (String raw : m.group(1).split(",")) {
				Identifier cat = Identifier.tryParse(raw.strip());
				if (cat == null) continue;
				if (cat.equals(BOOKSHELF)) { reachable = true; break; }
				for (Page lister : pages.values()) {
					if (lister.text().contains("### Category Entries: " + cat) && implies(entry.getValue().hook(), lister.hook())) {
						reachable = true;
						break;
					}
				}
			}
			if (!reachable) wrong.add(entry.getKey() + " (pack " + entry.getValue().hook().pack() + ") is listed only by pages that can be missing when it is there");
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** Whether {@code q}'s pack is on on every server {@code p}'s is. */
	private static boolean implies(MetacraftBooklet.Hook p, MetacraftBooklet.Hook q) {
		if (q.mods().isEmpty() || p.pack().equals(q.pack())) return true;
		if (p.mods().isEmpty()) return false;
		if (q.any()) return p.any() ? q.mods().containsAll(p.mods()) : p.mods().stream().anyMatch(q.mods()::contains);
		return p.any() ? p.mods().stream().allMatch(mod -> List.of(mod).containsAll(q.mods())) : p.mods().containsAll(q.mods());
	}

	@GameTest
	public void everyPageAnotherModOpensExists(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		OPENED_BY.forEach((mod, page) -> {
			if (FabricLoader.getInstance().isModLoaded(mod) && !BookletInit.PAGES.containsKey(page)) {
				wrong.add(mod + " opens " + page + ", which is not in the guide");
			}
		});
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyImageInTheGuideIsInThePack(GameTestHelper helper) {
		// Booklet finds an image in the resource pack; one that is not there is a gap on the page.
		var self = FabricLoader.getInstance().getModContainer(MetacraftBooklet.MOD_ID).orElseThrow();
		List<String> wrong = new ArrayList<>();
		for (var entry : pages(helper).entrySet()) {
			Matcher images = IMAGE.matcher(entry.getValue().text());
			while (images.find()) {
				Identifier image = Identifier.tryParse(images.group(1));
				boolean there = image != null && (Beside.wants(image)
						? Beside.has(image)
						: self.findPath("assets/" + image.getNamespace() + "/textures/booklet/image/" + image.getPath() + ".png").isPresent());
				if (!there) {
					wrong.add(entry.getKey() + " shows image " + images.group(1) + ", which is not in this mod's assets");
				}
			}
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** The PolyDecorations recipes METAcraft keeps: what the decorating chapter teaches. */
	private static final Pattern KEPT = Pattern.compile("canvas.*|[a-z_]+_(mailbox|sign_post)|rope|hammer|trowel");

	@GameTest
	public void polyDecorationsHasOnlyTheRecipesWeKeep(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded("polydecorations")) { helper.succeed(); return; }
		List<String> left = new ArrayList<>();
		List<String> kept = new ArrayList<>();
		for (var holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			Identifier id = holder.id().identifier();
			if (!id.getNamespace().equals("polydecorations")) continue;
			(KEPT.matcher(id.getPath()).matches() ? kept : left).add(id.getPath());
		}
		for (String needed : List.of("canvas", "oak_mailbox", "oak_sign_post", "rope", "hammer", "trowel")) {
			if (!kept.contains(needed)) helper.fail("polydecorations:" + needed + ", which the decorating chapter teaches, is gone");
		}
		if (!left.isEmpty()) {
			helper.fail(left.size() + " other PolyDecorations recipe(s) still loaded — a newer PolyDecorations? regenerate "
					+ "resourcepacks/polydecorations_s6: " + String.join(", ", left.subList(0, Math.min(8, left.size()))));
		}
		helper.succeed();
	}

	/**
	 * PolyDecorations' feature switches as a METAcraft config: they read as Season 6's (the dev run's
	 * file is Season 6's), and the file metacraft-config keeps writing still reads the way
	 * PolyDecorations reads it (Gson, from its mixin plugin), with the same switches.
	 */
	@GameTest
	public void polyDecorationsConfigIsSeason6AndStillReadable(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded("polydecorations")) { helper.succeed(); return; }
		var config = PolyDecorationsConfig.get();
		if (!config.equals(PolyDecorationsConfig.S6)) {
			helper.fail("PolyDecorations' features are not Season 6's: " + config.features());
		}
		try {
			var path = S6Defaults.path();
			var features = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject().getAsJsonObject("features");
			for (var entry : config.features().entrySet()) {
				var value = features.get(entry.getKey());
				var expected = switch (entry.getValue()) {
					case ON -> "true";
					case OFF -> "false";
					case HARD -> "\"hard\"";
				};
				if (value == null || !value.toString().equals(expected)) {
					helper.fail("config/polydecorations.json reads " + entry.getKey() + " = " + value + " to PolyDecorations, not " + expected);
				}
			}
		} catch (Exception e) {
			helper.fail("PolyDecorations can't read config/polydecorations.json as metacraft-config writes it: " + e);
		}
		helper.succeed();
	}

	/** The Recipes page names Polydex commands a player can type: each of them parses for a player who is not an operator. */
	@GameTest
	public void theRecipesPageCommandsExist(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded("polydex")) { helper.succeed(); return; }
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var source = player.createCommandSourceStack();
		var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
		for (String command : List.of("polydex", "polydex search patch",
				"polydex hover style polydex:sidebar", "polydex hover style polydex:bossbar", "polydex hover style polydex:disabled")) {
			var parse = dispatcher.parse(command, source);
			if (parse.getReader().canRead() || !parse.getExceptions().isEmpty() || parse.getContext().getCommand() == null) {
				helper.fail("/" + command + " does not parse for a player: " + parse.getExceptions());
			}
		}
		helper.succeed();
	}
}

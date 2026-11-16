package se.metacraft.kultur.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.metacraft.kultur.catalogue.Catalogue;
import se.metacraft.kultur.content.ModContent;
import se.metacraft.kultur.drinks.DrinkKind;
import se.metacraft.kultur.drinks.DrinkPotions;
import se.metacraft.kultur.drinks.DrunkTicker;
import se.metacraft.kultur.drinks.Drunkenness;
import se.metacraft.kultur.drinks.KulturDrinks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * What a vanilla client sees of the chapter patterns. A dedicated server runs in-process with
 * kultur; the client joins it as a vanilla client (PolymerHelloMixin), accepts the pack, and looks
 * at a white banner with the ITK pattern, holds a shield with the Pirkko pattern, has the Draken
 * painting on the wall beside them, and — since the main-hand shield shows its back, not its face —
 * two item frames on the same wall holding a decorated shield (face-on) and the Pirkko pattern item
 * itself, so the shield and pattern-item textures are both actually visible in the shot. The
 * main-hand shield is kept anyway, even though first person shows only its back: it is evidence
 * that the component syntax equips a live item, not just that the frames render one. The
 * screenshot is judged by eye — open it and look.
 *
 * Run: {@code ./gradlew :mods:metacraft-kultur:runClientGameTest} (opens a window). Screenshots
 * land in {@code mods/metacraft-kultur/build/run/clientGameTest/screenshots}.
 */
public final class KulturClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25597");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("gamemode creative Tester");
				// A wall behind the scene so the painting has something to hang on, a banner in
				// front of it with ITK in black on white, and the painting on the wall to the east.
				server.runCommand("fill -3 -60 4 3 -56 4 minecraft:stone");
				server.runCommand("setblock 0 -60 3 minecraft:white_banner[rotation=8]{patterns:[{pattern:\"kultur:itk\",color:\"black\"}]}");
				server.runCommand("summon minecraft:painting 2.5 -58.5 3.9 {facing:2b,variant:\"kultur:draken\"}");
				// The main-hand shield shows its back in first person, so the shield's decorated face and
				// the raw pattern item are also placed in item frames on the wall, facing the camera.
				server.runCommand("summon minecraft:item_frame -1.5 -57.5 3.5 {Facing:2b,Item:{id:\"minecraft:shield\",count:1,components:{\"minecraft:base_color\":\"white\",\"minecraft:banner_patterns\":[{pattern:\"kultur:itk\",color:\"black\"},{pattern:\"kultur:pirkko\",color:\"red\"}]}}}");
				server.runCommand("summon minecraft:item_frame -1.5 -58.5 3.5 {Facing:2b,Item:{id:\"kultur:pirkko_banner_pattern\",count:1}}");
				// The Pirkko pattern on a shield in the main hand, held so it shows in first person.
				server.runCommand("item replace entity Tester weapon.mainhand with minecraft:shield[minecraft:base_color=\"white\",minecraft:banner_patterns=[{pattern:\"kultur:itk\",color:\"black\"},{pattern:\"kultur:pirkko\",color:\"red\"}]]");
				server.runCommand("tp Tester 0.5 -60 -2.5 0 5");   // look south (+Z) at the banner, painting and frames
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_banner_shield_painting").withSize(1920, 1080));
				System.out.println("[kultur-clienttest] screenshot: " + shot);

				// A failed check is collected, not thrown, so the shots after it still get taken.
				List<String> failures = new ArrayList<>();
				chapterLoom(ctx, server, conn, failures);
				chapterBanners(ctx, server, conn, failures);

				if (FabricLoader.getInstance().isModLoaded("brewery")) {
					// The drinks, each in a frame on a wall of their own behind the camera, close up: PolymITer's
					// bottles, through Brewery.
					server.runCommand("fill -3 -60 -7 3 -57 -7 minecraft:stone");
					server.runCommand("item replace entity Tester weapon.mainhand with minecraft:air");
					server.runOnServer(WithBrewery::frameTheDrinks);
					server.runCommand("tp Tester -0.5 -60 -3.6 180 10");
					conn.waitForClientboundPackets();
					ctx.waitTicks(40);
					Path drinksShot = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_drinks").withSize(1920, 1080));
					System.out.println("[kultur-clienttest] screenshot: " + drinksShot);
				} else {
					drinksWithoutBrewery(ctx, server, conn);
				}
				if (!failures.isEmpty()) {
					throw new AssertionError(failures.size() + " kultur client check(s) failed:\n - " + String.join("\n - ", failures));
				}
			}
		}
	}

	private static void fail(List<String> failures, String what) {
		System.out.println("[kultur-clienttest] " + what);
		failures.add(what);
	}

	/** Every chapter's own pattern (its logo, free in the loom), in catalogue order. */
	private static List<Catalogue.Pattern> chapterPatterns() {
		List<Catalogue.Pattern> out = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Catalogue.load().patterns()) {
			if (owned.value().id().equals(owned.chapter().id())) out.add(owned.value());
		}
		return out;
	}

	/** Every catalogue pattern that is gated by an item, in catalogue order: the IT clubs' and Pirkko. */
	private static List<Catalogue.Pattern> gatedPatterns() {
		List<Catalogue.Pattern> out = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Catalogue.load().patterns()) {
			if (owned.value().item()) out.add(owned.value());
		}
		return out;
	}

	private static List<Identifier> selectable(LoomMenu menu) {
		List<Identifier> out = new ArrayList<>();
		for (var h : menu.getSelectablePatterns()) h.unwrapKey().ifPresent(k -> out.add(k.identifier()));
		return out;
	}

	/** A vanilla pattern that needs no item ({@code #minecraft:no_item_required}): the loom must list it with a banner and a dye. */
	private static final Identifier CONTROL = Identifier.withDefaultNamespace("stripe_bottom");

	/** What one side's loom lists, and whether its banner, dye and pattern slots are filled. */
	private record LoomView(boolean banner, boolean dye, boolean pattern, List<Identifier> selectable) {
		static LoomView of(LoomMenu menu) {
			return new LoomView(!menu.getBannerSlot().getItem().isEmpty(), !menu.getDyeSlot().getItem().isEmpty(),
					!menu.getPatternSlot().getItem().isEmpty(), KulturClientTests.selectable(menu));
		}
	}

	/**
	 * The loom, checked for every kultur pattern, old and new, on both sides: the server's {@code LoomMenu}
	 * and the vanilla client's own, which lists patterns from the registry and tags it was synced (the prod
	 * loom bug is in the client's list). Vanilla lists nothing unless both a banner and a dye are in, so
	 * every check has both: first the control (a vanilla pattern without an item must be listed, or every
	 * other check means nothing), then every pattern without an item, then each pattern with an item with
	 * that item in the pattern slot. Done with black dye, and again with More Dyes' Cerise when More Dyes
	 * is in (prod runs it). Failures go into {@code failures} as "loom (server): …" or "loom (client): …".
	 * The screenshot is the client's loom with black dye and the ITK pattern item in.
	 */
	private static void chapterLoom(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, List<String> failures) {
		List<Catalogue.Pattern> ungated = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Catalogue.load().patterns()) {
			if (!owned.value().item()) ungated.add(owned.value());
		}
		List<Catalogue.Pattern> gated = gatedPatterns();
		if (gated.isEmpty()) fail(failures, "loom: the catalogue has no pattern with an item");
		Catalogue.Pattern shown = gated.isEmpty() ? null : gated.getFirst();
		for (Catalogue.Pattern p : gated) {
			if (p.id().equals("itk")) shown = p;
		}
		for (Catalogue.Pattern p : chapterPatterns()) {
			if (p.item()) fail(failures, "loom: chapter pattern " + p.id() + " needs an item; the chapter logos are free");
		}
		List<Identifier> dyes = new ArrayList<>();
		dyes.add(Identifier.withDefaultNamespace("black_dye"));
		if (FabricLoader.getInstance().isModLoaded("moredyes")) {
			Identifier cerise = Identifier.fromNamespaceAndPath("moredyes", "cerise_dye");
			if (BuiltInRegistries.ITEM.containsKey(cerise)) dyes.add(cerise);
			else fail(failures, "loom: More Dyes is loaded but has no " + cerise);
		} else {
			System.out.println("[kultur-clienttest] loom: More Dyes is not loaded, vanilla dye only");
		}

		server.runCommand("setblock 5 -60 -2 minecraft:loom");
		boolean opened = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayerByName("Tester");
			var level = s.overworld();
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> new LoomMenu(id, inv, ContainerLevelAccess.create(level, new BlockPos(5, -60, -2))),
					Component.translatable("block.minecraft.loom")));
			return player.containerMenu instanceof LoomMenu;
		});
		if (!opened) {
			fail(failures, "loom (server): the loom menu did not open");
			return;
		}
		if (!awaitClient(ctx, conn, "the loom menu to open", v -> true)) {
			fail(failures, "loom (client): the loom menu did not open on the client");
			return;
		}

		for (Identifier dye : dyes) {
			String with = " with a banner and " + dye.getPath();
			// Banner and dye: the control and every pattern without an item.
			LoomView[] views = put(ctx, server, conn, dye, null, failures);
			if (views == null) continue;
			for (int side = 0; side < 2; side++) {
				String tag = side == 0 ? "loom (server): " : "loom (client): ";
				List<Identifier> list = views[side].selectable();
				if (!list.contains(CONTROL)) {
					fail(failures, tag + "control failed: vanilla " + CONTROL + " is not selectable" + with + " (" + list.size()
							+ " selectable), so no other loom check" + with + " means anything");
					continue;
				}
				for (Catalogue.Pattern p : ungated) {
					if (!list.contains(Identifier.fromNamespaceAndPath("kultur", p.id()))) {
						fail(failures, tag + p.id() + " (no item) is not selectable" + with);
					}
				}
			}
			System.out.println("[kultur-clienttest] loom" + with + ": " + views[0].selectable().size() + " selectable on the server, "
					+ views[1].selectable().size() + " on the client; " + ungated.size() + " pattern(s) without an item checked");
			// Each pattern with an item, with that item in the pattern slot.
			int checked = 0;
			for (Catalogue.Pattern p : gated) {
				if (ModContent.patternItem(p.id()) == null) {
					fail(failures, "loom: " + p.id() + " has no pattern item registered (" + p.itemPath() + ")");
					continue;
				}
				LoomView[] withItem = put(ctx, server, conn, dye, p.id(), failures);
				if (withItem == null) continue;
				checked++;
				Identifier want = Identifier.fromNamespaceAndPath("kultur", p.id());
				if (!withItem[0].selectable().contains(want)) {
					fail(failures, "loom (server): " + p.id() + " is not selectable" + with + " and " + p.itemPath() + " ("
							+ withItem[0].selectable().size() + " selectable)");
				}
				if (!withItem[1].selectable().contains(want)) {
					fail(failures, "loom (client): " + p.id() + " is not selectable" + with + " and " + p.itemPath() + " ("
							+ withItem[1].selectable().size() + " selectable)");
				}
			}
			System.out.println("[kultur-clienttest] loom" + with + ": " + checked + " of " + gated.size() + " pattern item(s) checked on both sides");
		}

		// Black dye and the shown pattern's item in the loom for the screenshot.
		if (shown != null) put(ctx, server, conn, dyes.getFirst(), shown.id(), failures);
		ctx.waitTicks(20);
		Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_chapter_loom").withSize(1920, 1080));
		System.out.println("[kultur-clienttest] screenshot: " + shot);
		server.runOnServer(s -> s.getPlayerList().getPlayerByName("Tester").closeContainer());
		ctx.waitTicks(10);
	}

	/**
	 * Empties the open loom, waits for the client to see it empty, then puts in a white banner, the dye
	 * and (if {@code pattern} is not null) that kultur pattern's item, and waits for the client to see
	 * them. Returns what the server's loom and the client's list, in that order, or null (with a failure)
	 * if the client never caught up.
	 */
	private static LoomView[] put(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn,
			Identifier dye, String pattern, List<String> failures) {
		server.runOnServer(s -> {
			if (!(s.getPlayerList().getPlayerByName("Tester").containerMenu instanceof LoomMenu menu)) return;
			menu.getPatternSlot().set(ItemStack.EMPTY);
			menu.getDyeSlot().set(ItemStack.EMPTY);
			menu.getBannerSlot().set(ItemStack.EMPTY);
		});
		if (!awaitClient(ctx, conn, "an empty loom", v -> !v.banner() && !v.dye() && !v.pattern())) {
			fail(failures, "loom (client): the client's loom never emptied (" + dye.getPath() + ", " + pattern + ")");
			return null;
		}
		LoomView onServer = server.computeOnServer(s -> {
			if (!(s.getPlayerList().getPlayerByName("Tester").containerMenu instanceof LoomMenu menu)) return null;
			menu.getBannerSlot().set(new ItemStack(Items.BANNER.white()));
			menu.getDyeSlot().set(new ItemStack(BuiltInRegistries.ITEM.getValue(dye)));
			if (pattern != null) menu.getPatternSlot().set(new ItemStack(ModContent.patternItem(pattern)));
			return LoomView.of(menu);
		});
		if (onServer == null) {
			fail(failures, "loom (server): the loom menu is no longer open");
			return null;
		}
		boolean wantPattern = pattern != null;
		if (!awaitClient(ctx, conn, "the banner and dye", v -> v.banner() && v.dye() && v.pattern() == wantPattern)) {
			fail(failures, "loom (client): the client's loom never showed the banner and " + dye.getPath()
					+ (wantPattern ? " and " + pattern + "'s item" : ""));
			return null;
		}
		LoomView onClient = ctx.computeOnClient(c -> c.player.containerMenu instanceof LoomMenu menu ? LoomView.of(menu) : null);
		return new LoomView[] {onServer, onClient};
	}

	/** Waits up to 5 s for the client's open loom to match {@code ready}; false if it never does. */
	private static boolean awaitClient(ClientGameTestContext ctx, TestDedicatedServerConnection conn, String what, Predicate<LoomView> ready) {
		conn.waitForClientboundPackets();
		for (int tick = 0; tick < 100; tick++) {
			LoomView view = ctx.computeOnClient(c -> c.player != null && c.player.containerMenu instanceof LoomMenu menu ? LoomView.of(menu) : null);
			if (view != null && ready.test(view)) return true;
			ctx.waitTick();
		}
		System.out.println("[kultur-clienttest] loom (client): gave up waiting for " + what);
		return false;
	}

	/**
	 * Every chapter pattern in white on a black banner, in a row against a stone wall, a few banners
	 * to a shot so each is legible. An empty view from the same spot is shot first; each banner shot
	 * has to differ from it.
	 */
	private static void chapterBanners(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, List<String> failures) {
		final int perShot = 6;
		List<Catalogue.Pattern> patterns = chapterPatterns();
		int shots = (patterns.size() + perShot - 1) / perShot;
		System.out.println("[kultur-clienttest] chapter banners: " + patterns.size() + " patterns in " + shots + " shots");
		server.runCommand("fill -4 -60 21 " + (perShot + 4) + " -56 21 minecraft:stone");
		server.runCommand("gamemode creative Tester");
		server.runCommand("item replace entity Tester weapon.mainhand with minecraft:air");
		server.runCommand("tp Tester " + (perShot / 2.0) + " -60 16.5 0 5");
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		Path empty = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_chapter_banners_empty").withSize(1920, 1080));
		for (int shot = 0; shot < shots; shot++) {
			int from = shot * perShot;
			int to = Math.min(patterns.size(), from + perShot);
			for (int i = from; i < to; i++) {
				int x = i - from + 1;
				server.runCommand("setblock " + x + " -60 20 minecraft:black_banner[rotation=8]{patterns:[{pattern:\"kultur:"
						+ patterns.get(i).id() + "\",color:\"white\"}]}");
			}
			for (int x = to - from + 1; x <= perShot; x++) server.runCommand("setblock " + x + " -60 20 minecraft:air");
			conn.waitForClientboundPackets();
			ctx.waitTicks(40);
			String name = shots == 1 ? "kultur_chapter_banners" : "kultur_chapter_banners_" + (shot + 1);
			Path path = ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080));
			System.out.println("[kultur-clienttest] screenshot: " + path + " (patterns "
					+ patterns.subList(from, to).stream().map(Catalogue.Pattern::id).toList() + ")");
			try {
				if (Files.mismatch(empty, path) == -1L) fail(failures, "banners: " + name + " is identical to the empty view");
			} catch (IOException e) {
				fail(failures, "banners: could not compare " + name + " with the empty view: " + e);
			}
		}
	}

	/**
	 * Without Brewery, Kultur brews the drinks itself: the four bottles, each in a frame on a wall behind
	 * the camera with a Spiken in hand, should be PolymITer's models, not plain potions. Then the
	 * inventory at 55 alcohol: "Inebriated IV" with PolymITer's icon (Luck, renamed by the pack), and
	 * the weakness and slowness of the steps below it.
	 */
	private static void drinksWithoutBrewery(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runCommand("fill -3 -60 -7 3 -57 -7 minecraft:stone");
		server.runOnServer(s -> {
			var level = s.overworld();
			DrinkKind[] kinds = DrinkKind.values();
			for (int i = 0; i < kinds.length; i++) {
				var frame = new ItemFrame(level, new BlockPos(i - 2, -59, -6), Direction.SOUTH);
				frame.setItem(stack(kinds[i]));
				level.addFreshEntity(frame);
			}
			s.getPlayerList().getPlayerByName("Tester").setItemInHand(InteractionHand.MAIN_HAND, stack(DrinkKind.SPIKEN));
		});
		server.runCommand("tp Tester -0.5 -60 -3.6 180 10");
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		Path bottles = ctx.takeScreenshot(TestScreenshotOptions.of("drinks_vanilla_bottles").withSize(1920, 1080));
		System.out.println("[kultur-clienttest] screenshot: " + bottles);

		server.runCommand("gamemode survival Tester");
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayerByName("Tester");
			Drunkenness.set(player, 55);
			DrunkTicker.refreshInebriated(player);
		});
		ctx.waitTicks(100);   // the steps' weakness and slowness come every 4 s
		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(20);
		Path inebriated = ctx.takeScreenshot(TestScreenshotOptions.of("drinks_vanilla_inebriated").withSize(1920, 1080));
		System.out.println("[kultur-clienttest] screenshot: " + inebriated);
		ctx.setScreen(() -> null);
		server.runOnServer(s -> Drunkenness.set(s.getPlayerList().getPlayerByName("Tester"), 0));
	}

	private static ItemStack stack(DrinkKind kind) {
		return kind == DrinkKind.NYCKELN ? new ItemStack(KulturDrinks.NYCKELN) : DrinkPotions.bottle(kind);
	}

	/** Everything that names a Brewery class, loaded only once Brewery is known to be there. */
	private static final class WithBrewery {
		static void frameTheDrinks(MinecraftServer s) {
			var level = s.overworld();
			String[] drinks = {"alcohol", "spiken", "slaggan", "nyckeln"};
			for (int i = 0; i < drinks.length; i++) {
				var frame = new net.minecraft.world.entity.decoration.ItemFrame(level, new net.minecraft.core.BlockPos(i - 2, -59, -6), net.minecraft.core.Direction.SOUTH);
				frame.setItem(eu.pb4.brewery.drink.DrinkUtils.createDrink(net.minecraft.resources.Identifier.fromNamespaceAndPath("kultur", drinks[i]),
						0, 10, i == 1 || i == 2 ? 1 : 0, net.minecraft.world.level.block.Blocks.FIRE));
				level.addFreshEntity(frame);
			}
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}

package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.component.DataComponents;
import se.metacraft.ovvar.content.ModComponents;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.EntityTypes;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Chapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import se.metacraft.ovvar.pack.ShaderPatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.PunkphozConfig;
import se.metacraft.ovvar.content.entity.Punkphoz;
import se.metacraft.ovvar.content.entity.PunkphozEntities;

/**
 * What a vanilla client sees of an ovve. A dedicated server runs in-process with ovvar; the test
 * client joins it (as a vanilla client, see PolymerHelloMixin), accepts the pack, wears an ovve
 * with patches, and looks at itself from the front. The screenshots are judged by the pixels the
 * patches' colours leave, and by two frames of the still scene being identical.
 *
 * Run: {@code ./gradlew :mods:ovvar:runClientGameTest} (opens a window). Screenshots land in
 * {@code build/run/clientGameTest/screenshots}.
 */
public final class OvvarClientTests implements FabricClientGameTest {
	/** Four patches on the front of the top: more than the dye colours carry, so the rest must come from the pack or the post pass. */
	private static final String PATCHES = "front_top_left.itk,front_top_right.nyckeln,front_low_left.it,front_low_right.data";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (ClientTestSupport.skipUnderShaderpack("OvvarClientTests")) return;
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25599");   // the two comparison servers hold 25565 and 25566
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
				// The subject is a mannequin, not the player: a mannequin stands still, so any change
				// between two frames is the shader, not idle sway. The player looks at it from a fixed spot.
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					OvveItem.setTopUp(ovve, true);
					Looks.setSewn(ovve, SpotPlacements.fromList(List.of(
							new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")),
							new Placement(Spot.FRONT_TOP_RIGHT, Patches.get("nyckeln")),
							new Placement(Spot.FRONT_LOW_LEFT, Patches.get("it")),
							new Placement(Spot.FRONT_LOW_RIGHT, Patches.get("data")))).getOrThrow());
					Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
					m.setPos(0.5, -60, 3.5);
					m.setYRot(180); m.setYBodyRot(180); m.setYHeadRot(180);
					m.setItemSlot(EquipmentSlot.LEGS, ovve);
					ItemStack top = new ItemStack(ModContent.top(Chapter.DATA));
					top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
					m.setItemSlot(EquipmentSlot.CHEST, top);
					level.addFreshEntity(m);
					// The Media frack: a chest-slot garment, no companion. Off to the side (west, +x is
					// east) so the ovve's shots are unchanged; the camera turns to it for its own shot.
					ItemStack frack = new ItemStack(ModContent.ovve(Chapter.MEDIA));
					Looks.setSewn(frack, SpotPlacements.fromList(List.of(
							new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")),
							new Placement(Spot.FRONT_LOW_RIGHT, Patches.get("it")))).getOrThrow());
					Mannequin f = new Mannequin(EntityTypes.MANNEQUIN, level);
					f.setPos(-5.5, -60, 3.5);
					f.setYRot(180); f.setYBodyRot(180); f.setYHeadRot(180);
					OvveItem.wear(f, frack);
					level.addFreshEntity(f);
				});
				// ITK and IT are not loot, so not instant: on the ovve and the frack they show only once the
				// pack holds the combos. Tester asks for it himself (a mannequin never claims a push), after
				// the mannequins' equipment has gone out and so queued their combos.
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				ClientTestSupport.reloadPackAndWait(ctx, () -> server.runCommand("execute as Tester run ovvar reload"),
						"the pack with the ovve's and the frack's combos");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");   // look south (+Z, yaw 0) straight at the mannequin
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);   // let the mannequin's spawn interpolation settle before the first shot
				ctx.runOnClient(client -> {
					ItemStack legs = client.player.getItemBySlot(EquipmentSlot.LEGS), chest = client.player.getItemBySlot(EquipmentSlot.CHEST);
					System.out.println("[ovvar-clienttest] client legs: " + legs + " equippable=" + legs.get(DataComponents.EQUIPPABLE) + " dye=" + legs.get(DataComponents.DYED_COLOR));
					System.out.println("[ovvar-clienttest] client chest: " + chest + " equippable=" + chest.get(DataComponents.EQUIPPABLE) + " dye=" + chest.get(DataComponents.DYED_COLOR));
				});
				server.runOnServer(mc -> {
					ItemStack legs = mc.getPlayerList().getPlayers().getFirst().getItemBySlot(EquipmentSlot.LEGS);
					System.out.println("[ovvar-clienttest] server legs: " + legs + " " + legs.getComponentsPatch());
				});

				Path first = ctx.takeScreenshot(TestScreenshotOptions.of("front_four_patches").withSize(1920, 1080));
				ctx.waitTicks(5);
				Path second = ctx.takeScreenshot(TestScreenshotOptions.of("front_four_patches_again").withSize(1920, 1080));

				BufferedImage a = read(first), b = read(second);
				Region box = Region.subjectBox(a);
				// ITK's green (86,214,46 and 120,232,80) and IT's purple ground (120,60,170) cannot come
				// from the cerise garment: their presence proves those patches drew. (Nyckeln and Data read
				// closer to the garment and make poorer probes.)
				ClientTestSupport.assertNoChecker(a, box.x0(), box.y0(), box.x1(), box.y1(), "mannequin");
				assertPresent(a, box, "itk (green)", (r, g, bl) -> g > 100 && g > r + 60 && bl < 140, 20);
				assertPresent(a, box, "it (lilac rim)", (r, g, bl) -> bl > 150 && bl > r + 25 && r > g + 20, 20);
				// The garment surface must be steady between two frames: the prototype's tag crawl changed
				// interior pixels by large amounts every frame, which this catches. A few silhouette pixels
				// shift by sub-pixel model interpolation (small deltas); those are tolerated.
				int flicker = flickering(a, b, box, 24);
				int budget = (box.x1() - box.x0()) * (box.y1() - box.y0()) / 100;   // 1% for residual edge motion
				if (flicker > budget) throw new AssertionError(flicker + " pixel(s) over the mannequin flickered by more than a step between two still frames (budget " + budget + ")");

				// The frack, straight on from three blocks north of it: the chest slot draws the coat and
				// its patches without a companion — the same probes, on a black coat.
				server.runCommand("tp Tester -5.5 -60 0.5 0 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				Path frackShot = ctx.takeScreenshot(TestScreenshotOptions.of("frack_front").withSize(1920, 1080));
				BufferedImage fr = read(frackShot);
				Region frackBox = Region.subjectBox(fr);
				assertPresent(fr, frackBox, "itk (green) on the frack", (r, g, bl) -> g > 100 && g > r + 60 && bl < 140, 20);
				assertPresent(fr, frackBox, "it (lilac rim) on the frack", (r, g, bl) -> bl > 150 && bl > r + 25 && r > g + 20, 20);
				// Near-black cloth: the coat itself, not the mannequin's skin.
				assertPresent(fr, frackBox, "the coat (near black)", (r, g, bl) -> r < 40 && g < 40 && bl < 40, 200);

				// /ovvar shaders: the link a player gets is the server's own, and it serves the very patcher jar.
				String url = server.computeOnServer(s -> ShaderPatcher.url(s.getPlayerList().getPlayerByName("Tester"))
						.orElseThrow(() -> new AssertionError("the server hosts no shader patcher")));
				System.out.println("[ovvar-clienttest] shader patcher at " + url);
				if (!url.endsWith("/ovvar-shader-patcher.jar")) throw new AssertionError("a browser would not save a .jar from " + url);
				byte[] served = download(url);
				byte[] bundled = bundledPatcher();
				if (!Arrays.equals(served, bundled)) throw new AssertionError(url + " served " + served.length + " bytes, not the " + bundled.length + "-byte patcher");
				server.runCommand("execute as Tester run ovvar shaders");
				ctx.waitTicks(20);   // the reply reaches the client before it leaves

				// Instant patches: sewn after the client loaded its pack and on mannequins, which never
				// claim a pack push, so whatever these show rides in the dye colour. Each ovve is worn
				// twice, one facing the camera and one turned away (east of the scene above, 6 blocks
				// apart). Only loot patches are instant, so the probes are loot ones. A: Nyckeln 0x0
				// (design 7, its 12x12) on the chest, JGS (10) on a back-top cell, Cerise (15) on the left
				// sleeve; Pung (5) on the seat and TMEIT (11) on the left leg. B: Nyckeln 0x0 on the big back
				// cell (its 16x16) with Playboy Pikachu (16) on a back-top cell over it, and METAcrew
				// (given, past what the dye colour names; a sleeve, so no trim either) on the left sleeve,
				// where it must not show until the pack has it.
				List<Placement> sewnA = List.of(
						new Placement(Spot.FRONT_TOP_LEFT, Patches.get("nyckeln0x0")),
						new Placement(Spot.BACK_TOP_RIGHT, Patches.get("jgs")),
						new Placement(Spot.SLEEVE_FRONT_TOP_L, Patches.get("cerise")),
						new Placement(Spot.SEAT, Patches.get("pung")),
						new Placement(Spot.LEG_FRONT_TOP_L, Patches.get("tmeit")));
				List<Placement> sewnB = List.of(
						new Placement(Spot.BACK_BIG, Patches.get("nyckeln0x0")),
						new Placement(Spot.BACK_TOP_LEFT, Patches.get("playboy-pikachu")),
						new Placement(Spot.SLEEVE_FRONT_TOP_L, Patches.get("metacrew")));
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					instantMannequin(level, 6.5, 180, sewnA);
					instantMannequin(level, 12.5, 0, sewnA);
					instantMannequin(level, 18.5, 180, sewnB);
					instantMannequin(level, 24.5, 0, sewnB);
				});
				conn.waitForClientboundPackets();
				ctx.runOnClient(client -> client.level.entitiesForRendering().forEach(e -> {
					if (e instanceof Mannequin m && m.getX() > 3) {
						System.out.println("[ovvar-clienttest] instant mannequin at x " + m.getX()
								+ ": chest dye=" + m.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR)
								+ " legs dye=" + m.getItemBySlot(EquipmentSlot.LEGS).get(DataComponents.DYED_COLOR));
					}
				}));
				BufferedImage aFront = instantShot(ctx, server, conn, "instant_a_front", "tp Tester 6.5 -60 0.5 0 0");
				assertPresent(aFront, Region.subjectBox(aFront), "nyckeln0x0 (green) on the instant chest", (r, g, bl) -> g > 100 && g > r + 60 && bl < 140, 20);
				instantShot(ctx, server, conn, "instant_a_back", "tp Tester 12.5 -60 0.5 0 0");
				instantShot(ctx, server, conn, "instant_b_front", "tp Tester 18.5 -60 0.5 0 0");
				BufferedImage bBack = instantShot(ctx, server, conn, "instant_b_back", "tp Tester 24.5 -60 0.5 0 0");
				assertPresent(bBack, Region.subjectBox(bBack), "nyckeln0x0 (green) on the instant big back cell", (r, g, bl) -> g > 100 && g > r + 60 && bl < 140, 20);
				// From straight above A: the arms' top faces carry no cell, so they show plain cloth.
				server.runCommand("gamemode spectator Tester");
				instantShot(ctx, server, conn, "instant_a_above", "tp Tester 6.5 -57.2 3.5 0 90");
			}
		}
		everyChapterLineup(ctx);
		punkphozShows(ctx);
	}

	// ---- Punkphöz

	/**
	 * What a vanilla client sees of Punkphöz: his model shows (the villager rig in Dante's look, drawn by Polymer), his head shakes
	 * when he is unhappy, a right-click opens a trade screen with the configured number of offers, the first offer can be bought,
	 * and he is still fine, and the client still connected, after being hurt once with a trader llama leashed to him. Compile-checked; runs in the cloud.
	 */
	private static void punkphozShows(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25602");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		PunkphozConfig config = OvvarConfig.get().punkphoz();
		// the pool is empty in this runtime, so no pool picks; kultur is not loaded, so no banner patterns
		int offers = config.chapterOvves() + config.patches();
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ClientTestSupport.acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				BufferedImage empty = read(ctx.takeScreenshot(TestScreenshotOptions.of("punkphoz_empty").withSize(1920, 1080)));

				server.runCommand("summon ovvar:punkphoz 0.5 -60 3.5 {NoAI:1b,Rotation:[180f,0f]}");
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				BufferedImage first = read(ctx.takeScreenshot(TestScreenshotOptions.of("punkphoz_front").withSize(1920, 1080)));
				int shown = differingInCrop(empty, first);
				System.out.println("[ovvar-clienttest] punkphoz changed pixels=" + shown);
				if (shown < 4000) throw new AssertionError("Punkphoz's model doesn't show: only " + shown + " pixels differ from the empty view");

				// the villager's unhappy shake: his head drops and rolls, so the head crop changes; then let it run out
				server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().level()
						.getEntities(PunkphozEntities.PUNKPHOZ, p -> true).getFirst().setUnhappyCounter(Punkphoz.UNHAPPY_TICKS));
				conn.waitForClientboundPackets();
				ctx.waitTicks(10);
				BufferedImage shaking = read(ctx.takeScreenshot(TestScreenshotOptions.of("punkphoz_shake").withSize(1920, 1080)));
				int shook = differingInHeadCrop(first, shaking);
				System.out.println("[ovvar-clienttest] punkphoz shake changed head pixels=" + shook);
				if (shook < 800) throw new AssertionError("Punkphoz's head doesn't shake: only " + shook + " head pixels changed");
				ctx.waitTicks(Punkphoz.UNHAPPY_TICKS + 10);
				// and back at rest once it has run out (the idle under it re-poses him)
				int rested = differingInHeadCrop(first, read(ctx.takeScreenshot(TestScreenshotOptions.of("punkphoz_after_shake").withSize(1920, 1080))));
				System.out.println("[ovvar-clienttest] punkphoz head pixels changed after the shake=" + rested);
				if (rested * 2 > shook) throw new AssertionError("Punkphoz's head didn't come back to rest: " + rested + " head pixels still differ (" + shook + " while shaking)");

				// right-click him the way a player does, through the use key
				ctx.getInput().pressKey(options -> options.keyUse);
				ctx.waitFor(client -> client.gui.screen() instanceof MerchantScreen, 100);
				ctx.waitTicks(10);
				int count = ctx.computeOnClient(client -> client.player.containerMenu instanceof MerchantMenu menu ? menu.getOffers().size() : -1);
				System.out.println("[ovvar-clienttest] punkphoz offers=" + count);
				if (count != offers) throw new AssertionError("Punkphoz shows " + count + " offers, not " + offers);

				// buy the first offer with the emeralds the server gives
				server.runCommand("give Tester minecraft:emerald 24");
				conn.waitForClientboundPackets();
				ctx.waitTicks(5);
				ctx.runOnClient(client -> {
					client.getConnection().send(new ServerboundSelectTradePacket(0));
					client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 2, 0, ContainerInput.QUICK_MOVE, client.player);
				});
				ctx.waitTicks(10);
				ctx.runOnClient(client -> client.player.closeContainer());
				ctx.waitTicks(5);

				// hurt once (no damage) with a trader llama leashed to him
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					Punkphoz punkphoz = level.getEntities(PunkphozEntities.PUNKPHOZ, p -> true).getFirst();
					punkphoz.hurtServer(level, level.damageSources().generic(), 0f);
					TraderLlama llama = new TraderLlama(EntityTypes.TRADER_LLAMA, level);
					llama.setPos(4.5, -60, 3.5); // four blocks to the side: well outside the centre crop the model is measured in
					level.addFreshEntity(llama);
					llama.setLeashedTo(punkphoz, true);
				});
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				if (!ctx.computeOnClient(client -> client.getConnection() != null && client.level != null)) throw new AssertionError("the client lost its connection");
				BufferedImage second = read(ctx.takeScreenshot(TestScreenshotOptions.of("punkphoz_after_hurt").withSize(1920, 1080)));
				int after = differingInCrop(empty, second);
				System.out.println("[ovvar-clienttest] punkphoz changed pixels after hurt and leash=" + after);
				if (after < 4000) throw new AssertionError("Punkphoz is gone or unseen after being hurt: only " + after + " pixels differ from the empty view");
			}
		}
	}

	/**
	 * How many pixels differ in a centre crop around Punkphöz (as the traders' client test measures its trader: 260 px wide, chest to
	 * knees, in the middle of the view; the 4000-pixel threshold is the same as there). The leashed llama stands four blocks to the
	 * side, about a thousand pixels from the middle, so it can't count towards the crop.
	 */
	private static int differingInCrop(BufferedImage a, BufferedImage b) {
		int x = a.getWidth() / 2 - 130, y = a.getHeight() * 4 / 10, w = 260, h = a.getHeight() * 3 / 10;
		return differing(a.getSubimage(x, y, w, h), b.getSubimage(x, y, w, h));
	}

	/** How many pixels differ around Punkphöz's head (3 blocks off at eye height: the band just above the middle of the view). */
	private static int differingInHeadCrop(BufferedImage a, BufferedImage b) {
		int x = a.getWidth() / 2 - 130, y = a.getHeight() * 3 / 10, w = 260, h = a.getHeight() / 4;
		return differing(a.getSubimage(x, y, w, h), b.getSubimage(x, y, w, h));
	}

	/** How many pixels of the two same-sized images differ noticeably. */
	private static int differing(BufferedImage a, BufferedImage b) {
		int n = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				int p = a.getRGB(x, y), q = b.getRGB(x, y);
				int diff = Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
				if (diff > 40) n++;
			}
		}
		return n;
	}

	// ---- every chapter's ovve, in a row

	/** Screen pixels per side of the colour crop. */
	private static final int CROP = 6;
	/** How far a crop's mean may stray from the shaded chapter colour, per channel. */
	private static final int COLOUR_TOLERANCE = 48;
	/**
	 * Vertical field of view for the lineup: set, not assumed (OvvarDanseClientTests leaves it at 50).
	 * The FOV effects (a flying spectator's ×1.1) are turned off with it, and the projection still
	 * reads the camera's own FOV for the frame rather than trusting this.
	 */
	private static final int LINEUP_FOV = 70;
	/** Mannequins per screenshot: six two blocks apart span ten blocks, well inside the frame from six blocks off. */
	private static final int PER_SHOT = 6;
	/**
	 * Vanilla's entity lighting on a face turned horizontally to the camera, in full daylight:
	 * ambient 0.4 plus 0.6 × the light that falls on it. The two level lights are (0.2, 1, -0.7) and
	 * (-0.2, 1, 0.7), normalised; a north face (the mannequins face north, at the camera) catches
	 * 0.7 / |(0.2, 1, -0.7)| = 0.566 of the first and none of the second: 0.4 + 0.6 × 0.566 = 0.74.
	 * The cloth on screen is the chapter colour times this, so that is what the crops are held to.
	 */
	private static final double FRONT_SHADE = 0.74;
	/** Avatars are drawn at 15/16 scale; one model unit is this many blocks. */
	private static final double UNIT = 0.9375 / 16;

	/** The top-up row stands six blocks south of the camera at z 0.5; the rolled-down row the same, twenty blocks further on, out of the first one's shots. */
	private static final double UP_ROW_Z = 6.5;
	private static final double DOWN_ROW_Z = 26.5;
	/** The camera stands this far in front (north) of a row. */
	private static final double LINEUP_DISTANCE = 6;

	/**
	 * One mannequin per chapter, top up and nothing sewn, in a row two blocks apart; then the rollable
	 * ones again, rolled down, in a second row. Each row is photographed from six blocks in front, six
	 * mannequins to a shot ({@code ovvar_chapters_up_1..3}, {@code ovvar_chapters_down_1..3}): the
	 * 35-block row does not fit one frame from there. Every mannequin's cloth, read off a 6×6 crop at
	 * the point its garment projects to, must be its chapter's colour (shaded, see FRONT_SHADE).
	 */
	private static void everyChapterLineup(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25606");   // every ovvar client test has its own port (25598-25602, 25606, 25607)
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		List<Chapter> up = List.of(Chapter.values());
		List<Chapter> down = up.stream().filter(chapter -> chapter.rollable).toList();
		int fovBefore = ctx.computeOnClient(client -> client.options.fov().get());
		double fovEffectBefore = ctx.computeOnClient(client -> client.options.fovEffectScale().get());
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ClientTestSupport.acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				// A spectator draws no hand in first person, so the row's ends are not behind one.
				server.runCommand("gamemode spectator Tester");
				server.runCommand("tp Tester 5.5 -60 0.5 0 0");
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					for (int i = 0; i < up.size(); i++) lineupMannequin(level, up.get(i), true, lineupX(i), UP_ROW_Z);
					for (int i = 0; i < down.size(); i++) lineupMannequin(level, down.get(i), false, lineupX(i), DOWN_ROW_Z);
				});
				conn.waitForClientboundPackets();
				ClientTestSupport.acceptResourcePack(ctx);   // only to settle: nothing is sewn, so no combos and no new pack
				// First person with the HUD hidden (F1): no hotbar or crosshair over the row either.
				ctx.runOnClient(client -> {
					client.options.setCameraType(CameraType.FIRST_PERSON);
					client.options.fov().set(LINEUP_FOV);
					client.options.fovEffectScale().set(0.0);
					if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
				});
				ctx.waitTicks(100);   // let the mannequins' spawn interpolation settle before the first shot
				List<String> wrong = new ArrayList<>();
				wrong.addAll(photographRow(ctx, server, conn, up, true, UP_ROW_Z, "ovvar_chapters_up"));
				wrong.addAll(photographRow(ctx, server, conn, down, false, DOWN_ROW_Z, "ovvar_chapters_down"));
				if (!wrong.isEmpty()) throw new AssertionError(wrong.size() + " mannequin(s) not in their chapter's colour:\n  " + String.join("\n  ", wrong));
			}
		} finally {
			ctx.runOnClient(client -> {
				client.options.fov().set(fovBefore);
				client.options.fovEffectScale().set(fovEffectBefore);
				if (client.gui.hud.isHidden()) client.gui.hud.toggle();
			});
		}
	}

	private static double lineupX(int i) {
		return 0.5 + 2 * i;
	}

	/** The chapter's garment on a mannequin facing north: an ovve top up (its top in the chest slot) or rolled down (chest bare), a frack in the chest slot. Nothing sewn. */
	private static void lineupMannequin(ServerLevel level, Chapter chapter, boolean topUp, double x, double z) {
		ItemStack garment = new ItemStack(ModContent.ovve(chapter));
		Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
		m.setPos(x, -60, z);
		m.setYRot(180); m.setYBodyRot(180); m.setYHeadRot(180);
		if (chapter.slot == EquipmentSlot.LEGS) OvveItem.setTopUp(garment, topUp);
		OvveItem.wear(m, garment);
		if (OvveItem.needsCompanionTop(garment)) m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModContent.top(chapter)));
		level.addFreshEntity(m);
	}

	/** Photographs a row six mannequins at a time, the camera six blocks in front of each group's middle; returns what was off colour. */
	private static List<String> photographRow(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn,
			List<Chapter> row, boolean topUp, double rowZ, String name) {
		List<String> wrong = new ArrayList<>();
		for (int from = 0, shot = 1; from < row.size(); from += PER_SHOT, shot++) {
			int to = Math.min(row.size(), from + PER_SHOT);
			double camX = (lineupX(from) + lineupX(to - 1)) / 2;
			BufferedImage img = instantShot(ctx, server, conn, name + "_" + shot,
					"tp Tester " + camX + " -60 " + (rowZ - LINEUP_DISTANCE) + " 0 0");
			Vec3 eye = ctx.computeOnClient(client -> client.player.getEyePosition());
			// The FOV the frame was drawn with (vertical, degrees), FOV effects included.
			double fov = ctx.computeOnClient(client -> (double) client.gameRenderer.mainCamera().getFov());
			System.out.println("[ovvar-clienttest] lineup " + name + "_" + shot + ": camera fov " + fov + ", eye " + eye);
			for (int i = from; i < to; i++) {
				String off = chestColour(img, eye, fov, row.get(i), topUp, lineupX(i), rowZ, name + "_" + shot);
				if (off != null) wrong.add(off);
			}
		}
		return wrong;
	}

	/**
	 * The mean of a 6×6 crop where this mannequin's cloth projects to, against its chapter's colour.
	 * Top up (or a frack): the chest, texel row 2 of the body's front (above the pockets), on the
	 * outer armour layer (one unit proud of the body). Its middle, except where that is not cloth:
	 * CL's and Doktorand's placket and a frack's open front, which are read at the chest's left third
	 * instead (texel column 1 of 8, the mannequin's right). Rolled down the chest is bare skin, so the
	 * rolled-down ovve is read on its right thigh (leggings layer, half a unit proud; texel row 3, the
	 * leg's middle). Returns null when it matches, else a line saying how it does not.
	 */
	private static String chestColour(BufferedImage img, Vec3 eye, double fov, Chapter chapter, boolean topUp,
			double x, double z, String shot) {
		double east, height, front;
		if (topUp || chapter.slot == EquipmentSlot.CHEST) {
			// The body box is 8 × 12 × 4 units, grown by 1 on every side: 10 wide over 8 texels, 14 tall
			// over 12, its front 3 units ahead of the middle. Its top is 1 unit above the neck, which is
			// 24 units (1.5 blocks, before the 15/16) above the feet.
			boolean leftThird = chapter == Chapter.CL || chapter == Chapter.DOKTORAND || chapter.slot == EquipmentSlot.CHEST;
			east = leftThird ? (5 - 1.5 * 10.0 / 8) * UNIT : 0;
			height = (24 + 1 - 2.5 * 14.0 / 12) * UNIT;
			front = 3 * UNIT;
		} else {
			// The right leg is 4 × 12 × 4 units, 1.9 units off the middle, grown by 0.5: 13 tall over 12
			// texels, from 12.5 units above the feet; its front 2.5 units ahead of the leg's middle.
			east = 1.9 * UNIT;
			height = (12.5 - 3.5 * 13.0 / 12) * UNIT;
			front = 2.5 * UNIT;
		}
		// The camera looks south (+z) level: east (+x) is screen left, up is screen up.
		double dx = x + east - eye.x, dy = -60 + height - eye.y, dz = z - front - eye.z;
		double focal = img.getHeight() / 2.0 / Math.tan(Math.toRadians(fov) / 2);
		int cx = (int) Math.round(img.getWidth() / 2.0 - focal * dx / dz);
		int cy = (int) Math.round(img.getHeight() / 2.0 - focal * dy / dz);
		int x0 = cx - CROP / 2, y0 = cy - CROP / 2;
		if (x0 < 0 || y0 < 0 || x0 + CROP > img.getWidth() || y0 + CROP > img.getHeight()) {
			return chapter.id + " in " + shot + ": its crop at (" + cx + ", " + cy + ") is off the screenshot";
		}
		long r = 0, g = 0, b = 0;
		for (int y = y0; y < y0 + CROP; y++) {
			for (int px = x0; px < x0 + CROP; px++) {
				int p = img.getRGB(px, y);
				r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
			}
		}
		int n = CROP * CROP;
		int[] mean = {(int) (r / n), (int) (g / n), (int) (b / n)};
		int[] want = {
				(int) Math.round(((chapter.colour >> 16) & 255) * FRONT_SHADE),
				(int) Math.round(((chapter.colour >> 8) & 255) * FRONT_SHADE),
				(int) Math.round((chapter.colour & 255) * FRONT_SHADE)};
		String line = chapter.id + " in " + shot + " at (" + cx + ", " + cy + "): mean " + Arrays.toString(mean)
				+ ", want " + Arrays.toString(want) + " (chapter colour " + String.format("#%06X", chapter.colour) + " shaded) ±" + COLOUR_TOLERANCE;
		System.out.println("[ovvar-clienttest] lineup " + line);
		for (int c = 0; c < 3; c++) if (Math.abs(mean[c] - want[c]) > COLOUR_TOLERANCE) return line;
		return null;
	}

	/** A Data ovve with these placements, top up and its top in the chest slot, worn by a mannequin at (x, -60, 3.5). */
	private static void instantMannequin(ServerLevel level, double x, float yaw, List<Placement> sewn) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		OvveItem.setTopUp(ovve, true);
		Looks.setSewn(ovve, SpotPlacements.fromList(sewn).getOrThrow());
		Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
		m.setPos(x, -60, 3.5);
		m.setYRot(yaw); m.setYBodyRot(yaw); m.setYHeadRot(yaw);
		m.setItemSlot(EquipmentSlot.LEGS, ovve);
		ItemStack top = new ItemStack(ModContent.top(Chapter.DATA));
		top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
		m.setItemSlot(EquipmentSlot.CHEST, top);
		level.addFreshEntity(m);
	}

	private static BufferedImage instantShot(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, String name, String tp) {
		server.runCommand(tp);
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		return read(ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080)));
	}

	private static byte[] bundledPatcher() {
		try {
			return Files.readAllBytes(FabricLoader.getInstance().getModContainer("ovvar").orElseThrow().findPath(ShaderPatcher.RESOURCE).orElseThrow());
		} catch (IOException e) {
			throw new AssertionError("the mod has no " + ShaderPatcher.RESOURCE, e);
		}
	}

	private static byte[] download(String url) {
		try {
			HttpResponse<byte[]> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) throw new AssertionError(url + " answered " + response.statusCode());
			return response.body();
		} catch (IOException | InterruptedException e) {
			throw new AssertionError("could not download " + url, e);
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ClientTestSupport.acceptResourcePack(ctx);
	}

	interface Rgb { boolean test(int r, int g, int b); }

	/** A box around the screen's centre where the third-person-front camera puts the player. */
	record Region(int x0, int y0, int x1, int y1) {
		/**
		 * The whole mannequin, arm sides included: at 1920×1080 the ovve spans about x centre ±150.
		 * The arm sides are where the strip wraps round the limb and the mapping is most fragile —
		 * the prototype's per-frame tag crawl showed there, not on the flat front — so the box must
		 * reach them, not stop at the torso.
		 */
		static Region subjectBox(BufferedImage img) {
			int w = img.getWidth(), h = img.getHeight();
			return new Region(w / 2 - 150, h / 2 - 40, w / 2 + 150, h / 2 + 185);
		}
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}

	private static void assertPresent(BufferedImage img, Region in, String what, Rgb match, int atLeast) {
		int n = 0;
		for (int y = in.y0(); y < in.y1(); y++) {
			for (int x = in.x0(); x < in.x1(); x++) {
				int p = img.getRGB(x, y);
				if (match.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		if (n < atLeast) throw new AssertionError(what + ": " + n + " matching pixel(s) in the player box, expected at least " + atLeast);
	}

	/** Pixels whose colour changed by more than {@code step} in any channel: real flicker, not antialiasing. */
	private static int flickering(BufferedImage a, BufferedImage b, Region in, int step) {
		int n = 0;
		for (int y = in.y0(); y < in.y1(); y++) {
			for (int x = in.x0(); x < in.x1(); x++) {
				int p = a.getRGB(x, y), q = b.getRGB(x, y);
				int dr = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255));
				int dg = Math.abs(((p >> 8) & 255) - ((q >> 8) & 255));
				int db = Math.abs((p & 255) - (q & 255));
				if (Math.max(dr, Math.max(dg, db)) > step) n++;
			}
		}
		return n;
	}
}

package se.metacraft.nether_rifts.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.ConfirmScreen;
import se.metacraft.nether_rifts.mob.BurningPatches;
import se.metacraft.nether_rifts.mob.GrenadierSword;
import se.metacraft.nether_rifts.mob.MagmaBomb;
import se.metacraft.nether_rifts.mob.RiftEntities;
import se.metacraft.nether_rifts.mob.WarGhast;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

/**
 * A vanilla client (no Polymer handshake) joins an in-process server and looks at rifts: a 2×3 portal at
 * each stage, close and from 8 blocks (vanilla's own portal swirl with the rift's cracks over it, staying put on
 * the world as the camera steps aside, and still when time stands still),
 * an L-shaped rift (merged panels must join without a seam), a 2×3 portal on the Z plane, and a
 * void-anchor rift in the same run (the shared item shader must draw both). It also looks at the War Drummer (it
 * must animate, its idle even without AI) and at a Chained Ghast with its deck crew, turned every way, shooting
 * and letting its crew off when it dies (screenshots; the artist's model has no checks on its look yet).
 */
public final class RiftClientTests implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (PanoramaCapture.CAPTURING) {
			return;
		}
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", String.valueOf(freePort()));
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set midnight");
				server.runCommand("gamemode spectator Tester");

				// 2×3 portal: frame x -1..2, y -60..-56 at z 0, inside x 0..1, y -59..-57
				server.runCommand("fill -1 -60 0 2 -56 0 minecraft:obsidian");
				server.runCommand("fill 0 -59 0 1 -57 0 minecraft:air");
				server.runCommand("setblock 0 -59 0 minecraft:fire");
				server.runCommand("tp Tester 0.5 -58 4 180 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(30);
				worldLocked(ctx, server, conn);
				int[] purple = new int[3];
				int[] nether = new int[3];
				double[] holes = new double[3];
				double[] redness = new double[3];
				int flashes = 0;
				int[] animated = new int[2];
				int orange = 0;
				String[] stages = {"lit", "unstable", "torn"};
				for (int i = 0; i < stages.length; i++) {
					String stage = stages[i];
					server.runCommand("rifts stage 0 -58 0 " + stage);
					ctx.waitTicks(10);
					Path a = ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_a").withSize(1920, 1080));
					ctx.waitTicks(5);
					Path b = ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_b").withSize(1920, 1080));
					ctx.waitTicks(25);
					var later = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_c").withSize(1920, 1080)));
					var first = read(a);
					var second = read(b);
					int rift = count(first, RiftClientTests::riftPixel);
					int moved = changed(first, second, 24);
					flashes += flashPixels(first) + flashPixels(second) + flashPixels(later);
					if (i == 2) {
						animated = holesAnimate(first, later);
						orange = orangeInRift(first) + orangeInRift(later);
					}
					purple[i] = count(first, RiftClientTests::purplePixel);
					nether[i] = count(first, RiftClientTests::netherPixel);
					holes[i] = holeShare(first);
					redness[i] = redness(first);
					System.out.println("[nether-rifts-clienttest] " + stage + " rift=" + rift + " purple=" + purple[i] + " nether=" + nether[i]
							+ " holes=" + String.format("%.3f", holes[i]) + " redness=" + String.format("%.3f", redness[i]) + " moved=" + moved);
					check(rift >= BOX / 20, "no rift on screen at " + stage + ": " + rift + " rift pixels");
					// the portal's own swirl animates under the cracks, from stage 1 on
					check(moved >= BOX / 20, "the rift doesn't move at " + stage + ": " + moved + " pixels changed");
					// and from 8 blocks, as it is mostly seen in play (to look at, not measured)
					server.runCommand("tp Tester 0.5 -58 8.5 180 0");
					conn.waitForClientboundPackets();
					ctx.waitTicks(3);
					ctx.takeScreenshot(TestScreenshotOptions.of("rift_" + stage + "_far").withSize(1920, 1080));
					server.runCommand("tp Tester 0.5 -58 4 180 0");
					conn.waitForClientboundPackets();
				}
				// Lit, the glass is clear but for hairline cracks: the vanilla portal's purple fills most of the box.
				check(purple[0] >= BOX / 3, "the lit rift doesn't show the portal's purple between its cracks: " + purple[0]);
				// The fallback sprite draws nothing at any stage; only the shader cracks the glass open as the rift
				// tears. More nether proves it ran.
				if (nether[2] < 2000 || nether[2] < nether[0] * 2) {
					check(false, "the torn rift shows no more nether than the lit one (shader not running?): nether lit=" + nether[0] + " torn=" + nether[2]);
				}
				// The glass's body, seen through the portal, reddens it from stage 2: the portal's purples lean
				// further to red at each stage.
				if (redness[1] < redness[0] + 0.04 || redness[2] < redness[1] + 0.04) {
					check(false, "the portal doesn't redden stage by stage: red/blue lit=" + String.format("%.3f", redness[0])
							+ " unstable=" + String.format("%.3f", redness[1]) + " torn=" + String.format("%.3f", redness[2]));
				}
				// Torn, whole shards have fallen out of the glass and the nether shows through the holes; lit, the glass
				// is whole but for cracks and a pinhole.
				if (holes[2] < 0.2 || holes[2] < holes[0] * 4) {
					check(false, "the torn rift shows no holes onto the nether: share of panorama in the rift lit="
							+ String.format("%.3f", holes[0]) + " torn=" + String.format("%.3f", holes[2]));
				}
				// The portal's haze drifts over the holes: in 1.5 s much of what shows the nether has changed. (The
				// panorama itself holds still; only the haze over it moves.)
				System.out.println("[nether-rifts-clienttest] torn holes=" + animated[0] + " animated=" + animated[1] + " flashes=" + flashes);
				if (animated[0] < 2000 || animated[1] < animated[0] / 4) {
					check(false, "the torn rift's holes don't animate: " + animated[1] + " of " + animated[0] + " hole pixels changed in 1.5 s");
				}
				// Nothing glitches any more: none of the old flash tiles (squares of pure magenta, cyan or white) at any
				// stage, in three shots each.
				check(flashes == 0, "the rift still flashes glitch tiles: " + flashes + " pixels");
				// Torn, the cracks keep their dark line and rose light and the holes a thin violet rim: no orange. Only
				// the red from behind the portal marks the stage.
				System.out.println("[nether-rifts-clienttest] torn orange=" + orange);
				check(orange <= 200, "the torn rift is still orange: " + orange + " pixels in two shots");
				stillWhenFrozen(ctx, server);
				server.runCommand("rifts collapse 0 -58 0");

				// an L of plain obsidian at z 10 (the frame itself must score nothing), opened with /rifts form
				server.runCommand("fill -2 -60 10 5 -53 10 minecraft:obsidian");
				server.runCommand("fill -1 -59 10 1 -55 10 minecraft:air");
				server.runCommand("fill 2 -59 10 4 -58 10 minecraft:air");
				server.runCommand("tp Tester 1.5 -57 15 180 0");
				ctx.waitTicks(30);
				var before = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_blob_before").withSize(1920, 1080)));
				int blobBefore = count(before, RiftClientTests::riftPixel);
				server.runCommand("rifts form 0 -58 10");
				ctx.waitTicks(30);
				var blob = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_blob").withSize(1920, 1080)));
				int blobPurple = count(blob, RiftClientTests::riftPixel);
				System.out.println("[nether-rifts-clienttest] blob before=" + blobBefore + " after=" + blobPurple);
				check(blobPurple >= blobBefore + BOX / 10, "forming didn't draw the L-shaped rift: " + blobBefore + " -> " + blobPurple);

				// a 2×3 portal on the Z plane (panels turned a quarter): frame z -21..-18 at x 20, inside z -20..-19, looked at from the east
				server.runCommand("fill 20 -60 -21 20 -56 -18 minecraft:obsidian");
				server.runCommand("fill 20 -59 -20 20 -57 -19 minecraft:air");
				server.runCommand("setblock 20 -59 -20 minecraft:fire");
				server.runCommand("tp Tester 24 -58 -19 90 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(30);
				var zA = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_z_plane_a").withSize(1920, 1080)));
				ctx.waitTicks(5);
				var zB = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_z_plane_b").withSize(1920, 1080)));
				int zPurple = count(zA, RiftClientTests::riftPixel);
				int zMoved = changed(zA, zB, 24);
				System.out.println("[nether-rifts-clienttest] z_plane purple=" + zPurple + " moved=" + zMoved);
				check(zPurple >= BOX / 20, "no Z-plane rift on screen: " + zPurple + " rift pixels");
				check(zMoved >= BOX / 200, "the Z-plane rift doesn't move: " + zMoved + " pixels changed");
				server.runCommand("rifts collapse 20 -58 -20");

				// the War Drummer, 4 blocks in front of the camera, facing it (yaw -90 is east; entities face their yaw, and the camera is east of it)
				server.runCommand("difficulty easy");
				server.runCommand("time set noon");
				server.runCommand("summon nether_rifts:war_drummer -20.5 -60 0.5 {Rotation:[-90f,0f],PersistenceRequired:1b}");
				server.runCommand("tp Tester -16.5 -58.5 0.5 90 10");
				ctx.waitTicks(25);
				var drummerA = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_a").withSize(1920, 1080)));
				ctx.waitTicks(5);
				var drummerB = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_b").withSize(1920, 1080)));
				// anywhere on screen: with its AI on, it turns about as it keeps its distance
				int drumMoved = changed(drummerA, drummerB, 24);
				System.out.println("[nether-rifts-clienttest] drummer moved=" + drumMoved);
				check(drumMoved >= 200, "the War Drummer doesn't animate: " + drumMoved);
				server.runCommand("kill @e[type=nether_rifts:war_drummer]");

				// a NoAI War Drummer (no beats: they come from its AI step) still breathes, sways and taps its sticks: its
				// idle plays on BIL's own holder tick. Three shots a third of a second apart must differ.
				server.runCommand("summon nether_rifts:war_drummer -20.5 -60 -12.5 {Rotation:[-90f,0f],NoAI:1b,PersistenceRequired:1b}");
				server.runCommand("tp Tester -17.5 -59.1 -12.5 90 8");
				conn.waitForClientboundPackets();
				ctx.waitTicks(30);
				var idleA = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_noai_a").withSize(1920, 1080)));
				ctx.waitTicks(7);
				var idleB = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_noai_b").withSize(1920, 1080)));
				ctx.waitTicks(7);
				var idleC = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_noai_c").withSize(1920, 1080)));
				int idleMoved = Math.max(changed(idleA, idleB, 24), changed(idleA, idleC, 24));
				System.out.println("[nether-rifts-clienttest] drummer noai idle moved=" + idleMoved);
				check(idleMoved >= 150, "a NoAI War Drummer doesn't animate: " + idleMoved + " pixels changed");
				// the same drummer from the front, held mid beat (frame 4) and at the top of the big beat's windup (frame 32: arms high)
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "beat", 4);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_front_beat").withSize(1920, 1080));
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "big_beat", 32);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_front_big_beat").withSize(1920, 1080));
				AnimationHold.release(server);
				server.runCommand("kill @e[type=nether_rifts:war_drummer]");

				// the War Drummer from its side, for a look: at rest, at the top of the big beat's windup (frame 32) and mid
				// beat. The model is the artist's own, so these are screenshots to eyeball, not checks.
				server.runCommand("tp Tester -20.5 -60.37 -26.5 180 0");
				server.runCommand("summon nether_rifts:war_drummer -20.5 -60 -30.5 {Rotation:[-90f,0f],NoAI:1b,PersistenceRequired:1b}");
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "big_beat", 0);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_side_rest").withSize(1920, 1080));
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "big_beat", 32);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_side_beat").withSize(1920, 1080));
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "beat", 4);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_side_hit").withSize(1920, 1080));
				AnimationHold.release(server);
				server.runCommand("kill @e[type=nether_rifts:war_drummer]");

				// War Drummers in armour, each beside a vanilla piglin in the same: iron, gold, diamond and chainmail; dyed
				// and undyed leather under an enchanted netherite helmet, copper leggings. From the front and the side, at
				// rest and arms up (big beat frame 32). Screenshots to eyeball against the piglins, not checks.
				String full = "equipment:{head:{id:\"minecraft:iron_helmet\"},chest:{id:\"minecraft:golden_chestplate\"},"
						+ "legs:{id:\"minecraft:diamond_leggings\"},feet:{id:\"minecraft:chainmail_boots\"}}";
				String leather = "equipment:{head:{id:\"minecraft:netherite_helmet\",components:{\"minecraft:enchantment_glint_override\":true}},"
						+ "chest:{id:\"minecraft:leather_chestplate\",components:{\"minecraft:dyed_color\":3368703}},"
						+ "legs:{id:\"minecraft:copper_leggings\"},feet:{id:\"minecraft:leather_boots\"}}";
				String still = "NoAI:1b,PersistenceRequired:1b,IsImmuneToZombification:1b,Rotation:[90f,0f]";
				server.runCommand("summon nether_rifts:war_drummer -60.5 -60 -1.5 {" + still + "," + full + "}");
				server.runCommand("summon minecraft:piglin -60.5 -60 0.5 {" + still + "," + full + "}");
				server.runCommand("summon nether_rifts:war_drummer -60.5 -60 2.5 {" + still + "," + leather + "}");
				server.runCommand("summon minecraft:piglin -60.5 -60 4.5 {" + still + "," + leather + "}");
				server.runCommand("tp Tester -65.5 -58.6 1.5 -90 8");
				conn.waitForClientboundPackets();
				ctx.waitTicks(30);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_armour_front").withSize(1920, 1080));
				AnimationHold.hold(server, RiftEntities.WAR_DRUMMER, "big_beat", 32);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_armour_front_arms_up").withSize(1920, 1080));
				AnimationHold.release(server);
				server.runCommand("tp Tester -60.5 -58.6 -5.5 0 8");
				ctx.waitTicks(20);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_armour_side").withSize(1920, 1080));
				server.runCommand("tp Tester -55.5 -58.6 1.5 90 8");
				ctx.waitTicks(20);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_drummer_armour_back").withSize(1920, 1080));
				server.runCommand("kill @e[type=nether_rifts:war_drummer]");
				server.runCommand("kill @e[type=minecraft:piglin]");

				// a Chained Ghast with its crew on the deck slung under it, 14 blocks off, turned to four yaws in turn (and then
				// turning a step a tick)
				server.runCommand("summon nether_rifts:war_ghast -40.5 -52 0.5 {Rotation:[-90f,0f],NoAI:1b,PersistenceRequired:1b}");
				server.runCommand("tp Tester -26.5 -53.2 0.5 90 0");
				conn.waitForClientboundPackets();
				ctx.waitTicks(25);
				for (int yaw = 0; yaw < 360; yaw += 90) {
					turn(server, yaw);
					ctx.waitTicks(15);
					ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_yaw_" + yaw).withSize(1920, 1080));
				}
				for (int i = 0; i < 6; i++) {
					turn(server, 285 + 25 * i);
					ctx.waitTicks(1);
					ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_turning_" + i).withSize(1920, 1080));
				}

				// facing the camera, its sad face, then its shooting face as it fires
				turn(server, -90);
				ctx.waitTicks(15);
				var calm = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_front").withSize(1920, 1080)));
				// (the shooting face shows from 0.44 s to 0.88 s into its shoot animation, which charging starts)
				charging(server, true);
				ctx.waitTicks(14);
				var shooting = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_shoot").withSize(1920, 1080)));
				charging(server, false);
				System.out.println("[nether-rifts-clienttest] war ghast face changed=" + changed(calm, shooting, 24));

				// up close: the crew on the deck from the front, the side and the back, and the deck over the ground from
				// below (screenshots to look at: the figures, the drummer's jacket, the tentacles, the deck's clearance)
				String[][] closeUps = {
						{"war_ghast_crew_front", tpLooking(-36.0, -55.6, 0.5, -40.5, -56.6, 0.5)},
						{"war_ghast_crew_side", tpLooking(-40.5, -55.6, 5.0, -40.5, -56.6, 0.5)},
						{"war_ghast_crew_back", tpLooking(-45.0, -55.6, 0.5, -40.5, -56.6, 0.5)},
						{"war_ghast_drummer_side", tpLooking(-39.0, -55.8, -4.0, -40.1, -56.4, -1.1)},
						{"war_ghast_tentacles", tpLooking(-40.5, -52.5, 6.5, -40.5, -51.5, 0.5)},
						{"war_ghast_deck_ground", tpLooking(-33.0, -59.3, 3.0, -40.5, -58.5, 0.5)}};
				for (var shot : closeUps) {
					server.runCommand(shot[1]);
					ctx.waitTicks(15);
					ctx.takeScreenshot(TestScreenshotOptions.of(shot[0]).withSize(1920, 1080));
				}

				// the ghast's gear where the artist marked it: the spear in the spear piglin's fist, the crossbow on the
				// crossbow piglin; each from in front of the figure and from its right
				for (var crew : new WarGhast.Crew[] {WarGhast.Crew.SPEAR, WarGhast.Crew.CROSSBOW}) {
					WarGhast.Spot[] spot = new WarGhast.Spot[1];
					server.runOnServer(s -> spot[0] = s.overworld().getEntities(RiftEntities.WAR_GHAST, e -> true).getFirst().spot(crew));
					var feet = spot[0].feet();
					double yaw = Math.toRadians(spot[0].yaw());
					double ax = -Math.sin(yaw), az = Math.cos(yaw);
					String[][] views = {{"front", String.valueOf(ax), String.valueOf(az)}, {"right", String.valueOf(-az), String.valueOf(ax)}};
					for (var view : views) {
						double dx = Double.parseDouble(view[1]), dz = Double.parseDouble(view[2]);
						server.runCommand(tpLooking(feet.x + dx * 2.4, feet.y + 1.4, feet.z + dz * 2.4, feet.x, feet.y + 0.8, feet.z));
						ctx.waitTicks(15);
						ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_" + crew.figure + "_" + view[0]).withSize(1920, 1080));
					}
				}

				// the crew burning (a Fire Aspect blow, a Flame arrow): the flames show on their stand-ins, never on the
				// zero-wide interactions clients see the crew as, which a client would draw flames over without end
				server.runCommand("weather clear");
				server.runOnServer(s -> s.overworld().getEntities(RiftEntities.CREW_HITBOX, e -> true).forEach(hitbox -> hitbox.igniteForSeconds(60)));
				String[][] burning = {
						{"war_ghast_crew_burning_front", tpLooking(-36.0, -55.6, 0.5, -40.5, -56.6, 0.5)},
						{"war_ghast_crew_burning_side", tpLooking(-40.5, -55.6, 5.0, -40.5, -56.6, 0.5)},
						{"war_ghast_crew_burning_back", tpLooking(-45.0, -55.6, 0.5, -40.5, -56.6, 0.5)},
						{"war_ghast_crew_burning_far", tpLooking(-26.5, -51.6, 0.5, -40.5, -54.6, 0.5)}};
				for (var shot : burning) {
					server.runCommand(shot[1]);
					ctx.waitTicks(15);
					ctx.takeScreenshot(TestScreenshotOptions.of(shot[0]).withSize(1920, 1080));
				}
				server.runOnServer(s -> s.overworld().getEntities(RiftEntities.CREW_HITBOX, e -> true).forEach(hitbox -> hitbox.clearFire()));

				// the artist's animations, each held on a frame, from the front close-up: over each, the crew and tentacles
				// keep idle's places (the import gives every animation idle's tracks)
				server.runCommand(tpLooking(-34.5, -55.0, 0.5, -40.5, -56.0, 0.5));
				String[][] held = {{WarGhast.SHOOT, "4"}, {WarGhast.SHOOT, String.valueOf(WarGhast.SHOT_FRAME)}, {WarGhast.DIVE, "6"},
						{WarGhast.CLIMB, "6"}, {WarGhast.DROP_BOMB, "4"}, {WarGhast.IDLE, "4"}};
				for (var hold : held) {
					AnimationHold.hold(server, RiftEntities.WAR_GHAST, hold[0], Integer.parseInt(hold[1]));
					ctx.waitTicks(10);
					ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_held_" + hold[0] + "_" + hold[1]).withSize(1920, 1080));
					AnimationHold.release(server);
					if (!hold[0].equals(WarGhast.IDLE)) {
						server.runOnServer(s -> s.overworld().getEntities(RiftEntities.WAR_GHAST, e -> true)
								.forEach(ghast -> ghast.getHolder().getAnimator().stopAnimation(hold[0])));
					}
				}

				// from 130 blocks off, past vanilla's display range (64 blocks, 96 at this view distance), with the view
				// distance to see that far (the server tracks entities as far as the client asks): the ghast, in the middle
				// of the screen, is still drawn, its displays' view range covering its tracking range
				ctx.runOnClient(client -> {
					client.options.renderDistance().set(12);
					client.options.broadcastOptions();
				});
				server.runCommand(tpLooking(-170.5, -50.0, 0.5, -40.5, -54.0, 0.5));
				ctx.waitTicks(100);
				var far = read(ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_far").withSize(1920, 1080)));
				// its grey body and tentacles and its brown deck; the sky's haze is bluer, the grass greener
				int farGhast = countIn(far, 760, 340, 1160, 740, (r, g, b) -> Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 12 && r > 100 && r < 230
						|| r > g + 15 && g > b && r < 200);
				System.out.println("[nether-rifts-clienttest] war ghast from 130 blocks px=" + farGhast);
				check(farGhast >= 30, "the war ghast isn't drawn from 130 blocks: " + farGhast + " px");
				ctx.runOnClient(client -> {
					client.options.renderDistance().set(5);
					client.options.broadcastOptions();
				});
				server.runCommand("tp Tester -26.5 -53.2 0.5 90 0");
				ctx.waitTicks(20);

				// the Grenadier on the deck, from the front left
				server.runCommand(tpLooking(-35.0, -55.0, -3.0, -41.3, -56.6, -0.1));
				ctx.waitTicks(15);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_bomber_rest").withSize(1920, 1080));

				// killed, the ghast lets its crew off: the drummer, the Grenadier and two piglins gliding down
				server.runCommand("tp Tester -26.5 -55.2 0.5 90 0");
				ctx.waitTicks(10);
				server.runCommand("kill @e[type=nether_rifts:war_ghast]");
				ctx.waitTicks(12);
				ctx.takeScreenshot(TestScreenshotOptions.of("war_ghast_crew_bail_out").withSize(1920, 1080));
				int[] crew = new int[1];
				server.runOnServer(s -> crew[0] = s.overworld().getEntities(RiftEntities.GHAST_BOMBARDIER, e -> true).size()
						+ s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.PIGLIN, e -> true).size()
						+ s.overworld().getEntities(RiftEntities.WAR_DRUMMER, e -> true).size());
				System.out.println("[nether-rifts-clienttest] crew after the kill=" + crew[0]);
				check(crew[0] == 4, "the war ghast's death let off " + crew[0] + " crew, not 4");
				ctx.waitTicks(40);
				server.runCommand("kill @e[type=nether_rifts:ghast_bombardier]");
				server.runCommand("kill @e[type=nether_rifts:war_drummer]");
				server.runCommand("kill @e[type=minecraft:piglin]");

				// /rifts showcase: the drummers (each in one animation, then in every armour), the Grenadier and a vanilla piglin
				// in rows, the ghasts behind, all named (screenshots to look at)
				server.runCommand("execute positioned 0.5 -60 40.5 rotated 0 0 run rifts showcase");
				conn.waitForClientboundPackets();
				server.runCommand(tpLooking(-4.5, -57.2, 38.0, -4.5, -59.0, 47.0));
				ctx.waitTicks(30);
				ctx.takeScreenshot(TestScreenshotOptions.of("showcase_left").withSize(1920, 1080));
				server.runCommand(tpLooking(5.5, -57.2, 38.0, 5.5, -59.0, 47.0));
				ctx.waitTicks(15);
				ctx.takeScreenshot(TestScreenshotOptions.of("showcase_right").withSize(1920, 1080));
				server.runCommand(tpLooking(0.5, -55.0, 33.0, 0.5, -56.0, 55.5));
				ctx.waitTicks(15);
				ctx.takeScreenshot(TestScreenshotOptions.of("showcase_ghasts").withSize(1920, 1080));
				// the Piglin General's row, 11 blocks ahead of the showcase (z 51.5): a General per animation but his death (idle,
				// walk, attack, spin_windup, spin, dizzy, roar), 3.3 apart from x 12.05 across to -7.75, then a Drummer beating
				// on a hoglin at x -11.05. The whole row first, looking down over the armour row; then three of his poses held
				// on one frame each (BIL plays 20 frames a second, and the spin is a blur otherwise) at the spin General, and
				// the Drummer's seat on the hoglin up close (its place was only set by numbers).
				server.runCommand(tpLooking(0.5, -50.0, 35.5, 0.5, -57.5, 51.5));
				ctx.waitTicks(15);
				ctx.takeScreenshot(TestScreenshotOptions.of("showcase_generals").withSize(1920, 1080));
				server.runCommand(tpLooking(-1.15, -57.2, 44.5, -1.15, -58.2, 51.5));
				for (var pose : new String[][] {{"spin", "3"}, {"spin_windup", "15"}, {"attack", "17"}}) {
					AnimationHold.hold(server, RiftEntities.PIGLIN_GENERAL, pose[0], Integer.parseInt(pose[1]));
					ctx.waitTicks(5);
					ctx.takeScreenshot(TestScreenshotOptions.of("showcase_general_" + pose[0]).withSize(1920, 1080));
				}
				AnimationHold.release(server);
				server.runCommand(tpLooking(-8.0, -57.4, 46.5, -11.05, -59.0, 51.5));
				ctx.waitTicks(15);
				ctx.takeScreenshot(TestScreenshotOptions.of("showcase_general_mounted_drummer").withSize(1920, 1080));
				server.runCommand("rifts showcase clear");

				// the Grenadier on foot: a vanilla piglin wearing the artist's goggles (an item model from the mod's pack, on its
				// head by the model's head display) and his gloved right arm over its own, the golden sword in its hand; from
				// the front and from its right. Then mid-throw: the arm with the bomb (as it swings, too), and let go. Only
				// logged: the lens count gives a later check its numbers.
				server.runCommand("summon nether_rifts:ghast_bombardier -20.5 -60 -12.5 {Rotation:[-90f,0f],NoAI:1b,PersistenceRequired:1b}");
				String front = "tp Tester -18.0 -58.6 -12.5 90 15";
				String side = "tp Tester -20.5 -58.6 -10.0 180 15";
				server.runCommand(front);
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				var onFoot = read(ctx.takeScreenshot(TestScreenshotOptions.of("ghast_bombardier_on_foot").withSize(1920, 1080)));
				int lenses = countAll(onFoot, (r, g, b) -> b > 160 && b > r + 50 && g > 100);
				System.out.println("[nether-rifts-clienttest] bombardier on foot lens=" + lenses);
				server.runCommand(side);
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("ghast_bombardier_on_foot_side").withSize(1920, 1080));
				for (var at : GrenadierSword.Throw.values()) {
					grenadierThrow(server, at);
					server.runCommand(front);
					ctx.waitTicks(10);
					ctx.takeScreenshot(TestScreenshotOptions.of("ghast_bombardier_throw_" + at.name().toLowerCase(java.util.Locale.ROOT)).withSize(1920, 1080));
					server.runCommand(side);
					ctx.waitTicks(10);
					ctx.takeScreenshot(TestScreenshotOptions.of("ghast_bombardier_throw_" + at.name().toLowerCase(java.util.Locale.ROOT) + "_side").withSize(1920, 1080));
				}
				grenadierThrow(server, GrenadierSword.Throw.BOMB);
				server.runOnServer(s -> s.overworld().getEntities(RiftEntities.GHAST_BOMBARDIER, e -> true)
						.forEach(b -> b.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT)));
				ctx.waitTicks(2);
				ctx.takeScreenshot(TestScreenshotOptions.of("ghast_bombardier_throw_swing_side").withSize(1920, 1080));
				grenadierThrow(server, null);
				server.runCommand("kill @e[type=nether_rifts:ghast_bombardier]");
				server.runCommand("difficulty peaceful");

				// the Grenadier's magma bomb close up, held still in the air: the artist's bomb in 3D, not magma cream's
				// flat sprite. Then a bomb's ground warning, from 7 blocks and from 20: the red ring as wide as the blast,
				// and in it the shadow of the bomb (held over it), darkening as it would come down. By day, as a shadow
				// takes the light it falls in.
				server.runCommand("time set noon");
				server.runCommand(tpLooking(-20.5, -58.4, -22.0, -20.5, -58.6, -24.5));
				conn.waitForClientboundPackets();
				ctx.waitTicks(5);
				hangBomb(server, -20.5, -58.6, -24.5);
				ctx.waitTicks(10);
				var bombShot = read(ctx.takeScreenshot(TestScreenshotOptions.of("magma_bomb_close").withSize(1920, 1080)));
				// the flat sprite's cream yellow and the bomb's dark shell and glowing core differ; log what's there
				System.out.println("[nether-rifts-clienttest] magma bomb close dark=" + count(bombShot, (r, g, b) -> r < 90 && g < 60 && b < 60)
						+ " glow=" + count(bombShot, (r, g, b) -> r > 200 && g > 90 && g < 200 && b < 80));
				server.runCommand("kill @e[type=nether_rifts:magma_bomb]");

				server.runCommand(tpLooking(-20.5, -55.5, -29.0, -20.5, -59.6, -35.5));
				conn.waitForClientboundPackets();
				ctx.waitTicks(10);
				var bare = read(ctx.takeScreenshot(TestScreenshotOptions.of("bomb_warning_none").withSize(1920, 1080)));
				warnUnder(server, -20.5, -35.5, 3.5);
				ctx.waitTicks(6);
				ctx.takeScreenshot(TestScreenshotOptions.of("bomb_warning_early").withSize(1920, 1080));
				ctx.waitTicks(18);
				var late = read(ctx.takeScreenshot(TestScreenshotOptions.of("bomb_warning_late").withSize(1920, 1080)));
				Rgb ringRed = (r, g, b) -> r > 180 && g < 120 && b < 90 && r > g + 90;
				int ring = countAll(late, ringRed) - countAll(bare, ringRed);
				// the shadow: the grass in the ring darker than it was
				int shaded = darker(bare, late, 30);
				System.out.println("[nether-rifts-clienttest] bomb warning ring=" + ring + " shaded=" + shaded);
				check(ring >= 3000, "the bomb warning's ring isn't drawn: " + ring + " red px");
				check(shaded >= 3000, "the bomb's shadow isn't drawn: " + shaded + " px darker");
				server.runCommand("kill @e[type=nether_rifts:magma_bomb]");
				ctx.waitTicks(10);

				server.runCommand(tpLooking(-20.5, -52.0, -16.0, -20.5, -59.6, -35.5));
				conn.waitForClientboundPackets();
				ctx.waitTicks(10);
				warnUnder(server, -20.5, -35.5, 3.5);
				ctx.waitTicks(20);
				var farWarning = read(ctx.takeScreenshot(TestScreenshotOptions.of("bomb_warning_far").withSize(1920, 1080)));
				int farRing = countAll(farWarning, ringRed);
				System.out.println("[nether-rifts-clienttest] bomb warning from 20 blocks ring=" + farRing);
				check(farRing >= 500, "the bomb warning's ring can't be seen from 20 blocks: " + farRing + " red px");
				server.runCommand("kill @e[type=nether_rifts:magma_bomb]");
				server.runCommand("time set midnight");

				// the void anchor's rift, same shader file
				server.runCommand("tp Tester 30.5 -50 0.5 0 90");
				ctx.waitTicks(10);
				server.runCommand("voidanchor rift 30.5 -57 0.5");
				ctx.waitTicks(20);
				var voidShot = read(ctx.takeScreenshot(TestScreenshotOptions.of("void_anchor_rift").withSize(1920, 1080)));
				int voidPx = count(voidShot, (r, g, bl) -> r >= 28 && r <= 70 && g < r && g >= r - 18 && bl > r + 5 && bl < r + 30);
				System.out.println("[nether-rifts-clienttest] void=" + voidPx);
				check(voidPx >= BOX / 100, "the void anchor's rift lost its shader: " + voidPx);
			}
		}
	}

	static final int BOX = 300 * 300;

	/** Set when the client runs Iris with this shaderpack (-Piris): the rifts are the pack's to draw, so log, don't fail. */
	static final String SHADERPACK = System.getenv("RIFTS_SHADERPACK");

	private static void check(boolean ok, String failure) {
		if (ok) return;
		if (SHADERPACK != null) {
			System.out.println("[nether-rifts-clienttest] under " + SHADERPACK + ": " + failure);
		} else {
			throw new AssertionError(failure);
		}
	}

	/**
	 * Nothing flickers: with the world's ticking frozen (the shader's time, the portal's swirl and the particles
	 * stand still) two shots of the unstable rift a quarter second apart must match. The cracks sit just clear
	 * of the portal's slab (0.135 of a block from the middle against its 0.125); cracks fighting it for depth
	 * would show as speckle, which the shots are there to look at.
	 */
	private static void stillWhenFrozen(ClientGameTestContext ctx, TestDedicatedServerContext server) {
		server.runCommand("rifts stage 0 -58 0 unstable");
		ctx.waitTicks(10);
		server.runCommand("tick freeze");
		ctx.waitTicks(5);
		var a = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_frozen_a").withSize(1920, 1080)));
		ctx.waitTicks(5);
		var b = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_frozen_b").withSize(1920, 1080)));
		server.runCommand("tick unfreeze");
		int moved = changed(a, b, 24);
		System.out.println("[nether-rifts-clienttest] frozen moved=" + moved);
		check(moved <= BOX / 1000, "the rift flickers while time stands still: " + moved + " pixels changed");
	}

	/** How far the portal's purples lean to red: their summed red over their summed blue, in the centre box. */
	private static double redness(BufferedImage img) {
		long r = 0, b = 0;
		int cx = img.getWidth() / 2, cy = img.getHeight() / 2;
		for (int y = cy - 150; y < cy + 150; y++) {
			for (int x = cx - 150; x < cx + 150; x++) {
				int p = img.getRGB(x, y);
				int pr = (p >> 16) & 255, pg = (p >> 8) & 255, pb = p & 255;
				if (purplePixel(pr, pg, pb)) {
					r += pr;
					b += pb;
				}
			}
		}
		return b == 0 ? 0 : r / (double) b;
	}

	/** Anything the rift draws: portal, cracks, rims, edge or the nether behind; none of which a flat world at night has. */
	static boolean riftPixel(int r, int g, int b) {
		return purplePixel(r, g, b) || netherPixel(r, g, b) || (r > 200 && b > 150);
	}

	/** The portal's purples (reddened from stage 2) and the edge's hot pink: blue and red well above green. */
	static boolean purplePixel(int r, int g, int b) {
		return b > g + 40 && r > g + 20;
	}

	/**
	 * The panorama's nether through the holes, under the portal's light purple haze: blue under red (the portal's
	 * purples run bluer), green at least a third of red (they have next to none), but not grey. Nor are the cracks'
	 * rose or the holes' violet rims (red over 200, which the hazed holes never reach).
	 */
	static boolean netherPixel(int r, int g, int b) {
		return r > 40 && r < 200 && r >= g + 16 && g * 3 >= r && b <= r * 0.95;
	}

	/**
	 * The cracks must be fixed to the world, not the screen. The camera steps a quarter block sideways without
	 * turning, so the rift slides across the screen; a pattern on the world slides with it, one on the screen
	 * stays put. Proxy: the shift of the rift's left edge (the geometry) against the shift that best matches a
	 * window well inside the rift between the two shots (the pattern: cracks, rims, swirl). They must agree,
	 * and the geometry must have moved.
	 */
	private static void worldLocked(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runCommand("rifts stage 0 -58 0 lit");
		ctx.waitTicks(20);
		var a = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_locked_a").withSize(1920, 1080)));
		server.runCommand("tp Tester 0.75 -58 4 180 0");
		conn.waitForClientboundPackets();
		ctx.waitTicks(3);
		var b = read(ctx.takeScreenshot(TestScreenshotOptions.of("rift_locked_b").withSize(1920, 1080)));
		server.runCommand("tp Tester 0.5 -58 4 180 0");
		conn.waitForClientboundPackets();
		ctx.waitTicks(3);
		int[] boxA = riftBox(a);
		if (boxA == null) {
			check(false, "no rift on screen to step past");
			return;
		}
		int geometry = leftEdge(b, boxA) - leftEdge(a, boxA);
		int x0 = boxA[0] + 70, x1 = boxA[2] - 70, y0 = boxA[1] + 40, y1 = boxA[3] - 40;
		int best = 0;
		double bestDiff = Double.MAX_VALUE, atZero = 0;
		for (int dx = -120; dx <= 120; dx++) {
			double diff = 0;
			for (int y = y0; y < y1; y += 2) {
				for (int x = x0; x < x1; x += 2) {
					int p = a.getRGB(x, y), q = b.getRGB(x + dx, y);
					diff += Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs((p & 255) - (q & 255));
				}
			}
			diff /= ((y1 - y0) / 2.0) * ((x1 - x0) / 2.0);
			if (dx == 0) atZero = diff;
			if (diff < bestDiff) {
				bestDiff = diff;
				best = dx;
			}
		}
		System.out.println("[nether-rifts-clienttest] locked box=" + Arrays.toString(boxA) + " geometry=" + geometry + " pattern=" + best
				+ " diff=" + String.format("%.1f", bestDiff) + " diffAtZero=" + String.format("%.1f", atZero));
		check(Math.abs(geometry) >= 20, "the camera step didn't move the rift on screen: " + geometry + " px");
		check(Math.abs(best - geometry) <= 4, "the rift's pattern doesn't move with the world: rift moved " + geometry + " px, pattern " + best + " px");
	}

	/**
	 * The share of the rift, its edge left out, that shows the panorama rather than the portal. The rift is
	 * taken as the rows and columns with 60 rift pixels or more, so portal particles drifting round it don't stretch
	 * the box over the frame.
	 */
	private static double holeShare(BufferedImage img) {
		int[] box = innerBox(img);
		if (box == null) return 0;
		return countIn(img, box[0], box[1], box[2], box[3], RiftClientTests::netherPixel) / (double) ((box[2] - box[0]) * (box[3] - box[1]));
	}

	/**
	 * The rift's box (x0, y0, x1, y1) less 30 px all round, its edge left out: the rows and columns with 60 rift
	 * pixels or more, so portal particles drifting round it don't stretch the box over the frame. Null if none.
	 */
	private static int[] innerBox(BufferedImage img) {
		return riftFrame(img, 30);
	}

	/** The rift's box (x0, y0, x1, y1) less {@code inset} px all round: see {@link #innerBox}. Null if none. */
	private static int[] riftFrame(BufferedImage img, int inset) {
		int[] rows = new int[img.getHeight()];
		int[] cols = new int[img.getWidth()];
		for (int y = 300; y < img.getHeight(); y++) {
			for (int x = 600; x < 1400; x++) {
				int p = img.getRGB(x, y);
				if (riftPixel((p >> 16) & 255, (p >> 8) & 255, p & 255)) {
					rows[y]++;
					cols[x]++;
				}
			}
		}
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
		for (int y = 0; y < rows.length; y++) {
			if (rows[y] >= 60) {
				y0 = Math.min(y0, y);
				y1 = y;
			}
		}
		for (int x = 0; x < cols.length; x++) {
			if (cols[x] >= 60) {
				x0 = Math.min(x0, x);
				x1 = x;
			}
		}
		if (x1 < 0 || y1 < 0) return null;
		x0 += inset;
		y0 += inset;
		x1 -= inset;
		y1 -= inset;
		if (x1 <= x0 || y1 <= y0) return null;
		return new int[]{x0, y0, x1, y1};
	}

	/**
	 * The rift's hole pixels in {@code a} (nether in both shots, in the rift's inner box) and how many of them
	 * changed by more than 12 in some channel by {@code b}.
	 */
	private static int[] holesAnimate(BufferedImage a, BufferedImage b) {
		int[] box = innerBox(a);
		if (box == null) return new int[2];
		int holes = 0, moved = 0;
		for (int y = box[1]; y < box[3]; y++) {
			for (int x = box[0]; x < box[2]; x++) {
				int p = a.getRGB(x, y), q = b.getRGB(x, y);
				if (!netherPixel((p >> 16) & 255, (p >> 8) & 255, p & 255) || !netherPixel((q >> 16) & 255, (q >> 8) & 255, q & 255)) continue;
				holes++;
				for (int s = 0; s <= 16; s += 8) {
					if (Math.abs(((p >> s) & 255) - ((q >> s) & 255)) > 12) {
						moved++;
						break;
					}
				}
			}
		}
		return new int[]{holes, moved};
	}

	/** Orange pixels (bright red, middling green, little blue) in the rift's box, its edge strip in. */
	private static int orangeInRift(BufferedImage img) {
		int[] box = riftFrame(img, 0);
		if (box == null) return 0;
		return countIn(img, box[0], box[1], box[2] + 1, box[3] + 1, (r, g, b) -> r > 200 && g > 80 && g < 170 && b < 90);
	}

	/** The old glitch's flash colours: pure magenta, cyan and white. */
	private static final int[][] FLASHES = {{255, 41, 199}, {41, 240, 255}, {250, 250, 250}};

	/**
	 * Pixels of flash tiles in the middle of the screen: the corner of a square at least 11 px across (a tile was 2
	 * world pixels, about 20 px here) all one flash colour, which rims and sparks never are, nor the white heart of
	 * a soul-fire flame (up to about 8 px).
	 */
	private static int flashPixels(BufferedImage img) {
		int n = 0;
		for (int y = 300; y < img.getHeight() - 10; y++) {
			for (int x = 600; x < 1400; x++) {
				int f = flash(img.getRGB(x, y));
				if (f >= 0 && flash(img.getRGB(x + 10, y)) == f && flash(img.getRGB(x, y + 10)) == f && flash(img.getRGB(x + 10, y + 10)) == f) n++;
			}
		}
		return n;
	}

	/** Which flash colour p is, to within 8 a channel; -1 if none. */
	private static int flash(int p) {
		int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
		for (int i = 0; i < FLASHES.length; i++) {
			if (Math.abs(r - FLASHES[i][0]) <= 8 && Math.abs(g - FLASHES[i][1]) <= 8 && Math.abs(b - FLASHES[i][2]) <= 8) return i;
		}
		return -1;
	}

	/** The rift's bounding box (x0, y0, x1, y1) in the middle of the screen, chat and toasts left out; null if there's none. */
	private static int[] riftBox(BufferedImage img) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
		for (int y = 300; y < img.getHeight(); y++) {
			for (int x = 600; x < 1400; x++) {
				int p = img.getRGB(x, y);
				if (riftPixel((p >> 16) & 255, (p >> 8) & 255, p & 255)) {
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
		}
		return x1 < 0 ? null : new int[]{x0, y0, x1, y1};
	}

	/** The median over the box's middle rows of the leftmost rift pixel; -1 if no row has one. */
	private static int leftEdge(BufferedImage img, int[] box) {
		var lefts = new ArrayList<Integer>();
		for (int y = box[1] + 40; y < box[3] - 40; y += 4) {
			for (int x = 600; x < 1400; x++) {
				int p = img.getRGB(x, y);
				if (riftPixel((p >> 16) & 255, (p >> 8) & 255, p & 255)) {
					lefts.add(x);
					break;
				}
			}
		}
		if (lefts.isEmpty()) return -1;
		Collections.sort(lefts);
		return lefts.get(lefts.size() / 2);
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}

	interface Rgb {
		boolean test(int r, int g, int b);
	}

	/** Pixels in the 300×300 box at the centre of the screen that match. */
	private static int count(BufferedImage img, Rgb rgb) {
		int n = 0;
		int cx = img.getWidth() / 2, cy = img.getHeight() / 2;
		for (int y = cy - 150; y < cy + 150; y++) {
			for (int x = cx - 150; x < cx + 150; x++) {
				int p = img.getRGB(x, y);
				if (rgb.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		return n;
	}

	/** Pixels anywhere on screen that match. */
	private static int countAll(BufferedImage img, Rgb rgb) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y);
				if (rgb.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		return n;
	}

	/** Pixels in the box [x0, x1) × [y0, y1) that match. */
	private static int countIn(BufferedImage img, int x0, int y0, int x1, int y1, Rgb rgb) {
		int n = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				int p = img.getRGB(x, y);
				if (rgb.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		return n;
	}

	/** Pixels in the centre box that changed by more than {@code step} in some channel. */
	private static int changed(BufferedImage a, BufferedImage b, int step) {
		int n = 0;
		int cx = a.getWidth() / 2, cy = a.getHeight() / 2;
		for (int y = cy - 150; y < cy + 150; y++) {
			for (int x = cx - 150; x < cx + 150; x++) {
				int p = a.getRGB(x, y), q = b.getRGB(x, y);
				for (int s = 0; s <= 16; s += 8) {
					if (Math.abs(((p >> s) & 255) - ((q >> s) & 255)) > step) {
						n++;
						break;
					}
				}
			}
		}
		return n;
	}

	/** Turns the overworld's war ghasts to {@code yaw}, body and head (the model follows the body's yaw). */
	private static void turn(TestDedicatedServerContext server, float yaw) {
		server.runOnServer(s -> {
			for (var ghast : s.overworld().getEntities(RiftEntities.WAR_GHAST, e -> true)) {
				ghast.setYRot(yaw);
				ghast.setYBodyRot(yaw);
				ghast.setYHeadRot(yaw);
			}
		});
	}

	/** A {@code tp} putting the Tester's eye (1.62 over its feet) at (x, y, z), looking at (tx, ty, tz). */
	static String tpLooking(double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x, dy = ty - y, dz = tz - z;
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
		return String.format(java.util.Locale.ROOT, "tp Tester %.3f %.3f %.3f %.2f %.2f", x, y - 1.62, z, yaw, pitch);
	}

	/** Sets where a throw is on the overworld's Grenadiers' swords (null: none, the sword shows). */
	private static void grenadierThrow(TestDedicatedServerContext server, GrenadierSword.Throw at) {
		server.runOnServer(s -> s.overworld().getEntities(RiftEntities.GHAST_BOMBARDIER, e -> true)
				.forEach(b -> GrenadierSword.setThrow(b.getMainHandItem(), at)));
	}

	/** Sets the overworld's war ghasts charging a fireball (their shooting face) or not. */
	private static void charging(TestDedicatedServerContext server, boolean charging) {
		server.runOnServer(s -> s.overworld().getEntities(RiftEntities.WAR_GHAST, e -> true).forEach(ghast -> ghast.setCharging(charging)));
	}

	/** A magma bomb held still in the air at (x, y, z) (no gravity, no speed), for a look at it. */
	private static void hangBomb(TestDedicatedServerContext server, double x, double y, double z) {
		server.runOnServer(s -> {
			var bomb = new MagmaBomb(s.overworld(), null);
			bomb.setNoGravity(true);
			bomb.setPos(x, y, z);
			bomb.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			s.overworld().addFreshEntity(bomb);
		});
	}

	/** A bomb's ground warning on the ground under (x, z), the bomb held {@code height} blocks over it. */
	private static void warnUnder(TestDedicatedServerContext server, double x, double z, double height) {
		server.runOnServer(s -> {
			var ground = BurningPatches.groundBelow(s.overworld(), new net.minecraft.world.phys.Vec3(x, -50, z));
			BurningPatches.warn(s.overworld(), ground, null);
		});
		hangBomb(server, x, -60 + height, z);
	}

	/** Pixels anywhere on screen that got darker from {@code a} to {@code b} by more than {@code step} (by their sum of channels / 3). */
	private static int darker(BufferedImage a, BufferedImage b, int step) {
		int n = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				int p = a.getRGB(x, y), q = b.getRGB(x, y);
				int la = (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255)) / 3;
				int lb = (((q >> 16) & 255) + ((q >> 8) & 255) + (q & 255)) / 3;
				if (la - lb > step) n++;
			}
		}
		return n;
	}

	/** A port nothing listens on, so runs in several checkouts at once don't collide. */
	static int freePort() {
		try (var socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}

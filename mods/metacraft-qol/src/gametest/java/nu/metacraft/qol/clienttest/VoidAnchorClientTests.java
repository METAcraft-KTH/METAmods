package nu.metacraft.qol.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import nu.metacraft.qol.void_anchor.AnchorBinding;
import nu.metacraft.qol.void_anchor.rift.RiftTracker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What a vanilla client sees of a rift. A dedicated server runs in-process with the mod; the test
 * client joins it as a vanilla client (PolymerHelloMixin), accepts the pack, hovers above a rift
 * and looks straight down at it.
 *
 * The painted sprite's split is a near-black purple (about 10 4 24); the shader shows the End's
 * void through it, a lighter grey-violet (about 45 37 58). That void on screen therefore proves the
 * pack's item shader ran, and a change between two frames proves it moves.
 *
 * Run: {@code ./gradlew :mods:metacraft-qol:runClientGameTest} (opens a window).
 * Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public final class VoidAnchorClientTests implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25601");
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
				server.runCommand("gamemode spectator Tester");
				server.runCommand("tp Tester 0.5 -50 0.5 0 90");   // hover, looking straight down
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				server.runCommand("voidanchor rift 0.5 -57 0.5");
				ctx.waitTicks(20);   // past the opening animation
				Path first = ctx.takeScreenshot(TestScreenshotOptions.of("rift_a").withSize(1920, 1080));
				ctx.waitTicks(6);
				Path second = ctx.takeScreenshot(TestScreenshotOptions.of("rift_b").withSize(1920, 1080));

				BufferedImage a = read(first), b = read(second);
				int box = (300 * 300);
				// The rift is a thin crack, so count every pixel of it: its magenta and violet, and the
				// near-white of its hottest lines (none of which the grass or sky have).
				int purple = count(a, (r, g, bl) -> (bl > g + 40 && r > g + 20) || (r > 200 && bl > 200 && g < r - 10));
				int voidPx = count(a, (r, g, bl) -> r >= 28 && r <= 70 && g < r && g >= r - 18 && bl > r + 5 && bl < r + 30);
				int moved = changed(a, b, 24);
				System.out.println("[qol-clienttest] purple=" + purple + " void=" + voidPx + " moved=" + moved + " of " + box);
				if (purple < box / 90) throw new AssertionError("no rift on screen: " + purple + " magenta or white-hot pixels in the centre");
				if (voidPx < box / 100) throw new AssertionError("the rift shader did not run: " + voidPx + " void pixels in the centre");
				if (moved < box / 200) throw new AssertionError("the rift does not move: " + moved + " pixels changed between frames");

				// Seen at an angle, for the depth: the stars inside shift against the edge.
				server.runCommand("voidanchor rift 0.5 -57 0.5");
				server.runCommand("tp Tester 4.5 -53.5 4.5 135 40");
				ctx.waitTicks(20);
				ctx.takeScreenshot(TestScreenshotOptions.of("rift_angle").withSize(1920, 1080));

				// The shatter style in the End's void, each its own rift, once fully open: from above, at an angle, side on.
				server.runCommand("execute in minecraft:the_end run tp Tester 300.5 40 0.5 0 90");
				ctx.waitTicks(40);
				server.runCommand("execute in minecraft:the_end run voidanchor rift shatter 300.5 33 0.5");
				ctx.waitTicks(30);
				ctx.takeScreenshot(TestScreenshotOptions.of("shatter_above").withSize(1920, 1080));
				server.runCommand("execute in minecraft:the_end run voidanchor rift shatter 340.5 33 0.5");
				server.runCommand("execute in minecraft:the_end run tp Tester 345.5 36.5 5.5 135 30");
				ctx.waitTicks(30);
				ctx.takeScreenshot(TestScreenshotOptions.of("shatter_angle").withSize(1920, 1080));
				server.runCommand("execute in minecraft:the_end run voidanchor rift shatter 380.5 33 0.5");
				server.runCommand("execute in minecraft:the_end run tp Tester 380.5 33.2 7.5 180 0");
				ctx.waitTicks(30);
				ctx.takeScreenshot(TestScreenshotOptions.of("shatter_side").withSize(1920, 1080));
				server.runCommand("execute in minecraft:the_end run voidanchor rift crack 420.5 33 0.5");
				server.runCommand("execute in minecraft:the_end run tp Tester 425.5 36.5 5.5 135 30");
				ctx.waitTicks(30);
				ctx.takeScreenshot(TestScreenshotOptions.of("crack_end_angle").withSize(1920, 1080));
				// A real fall, as the player sees it: a survival player bound to a charged anchor walks
				// off into the void looking ahead, and is caught.
				server.runCommand("execute in minecraft:the_end run fill 498 59 -2 502 59 2 minecraft:end_stone");
				server.runCommand("execute in minecraft:the_end run setblock 500 60 0 metacraft:void_anchor[charges=4]");
				server.runOnServer(s -> AnchorBinding.bind(
						s.getPlayerList().getPlayerByName("Tester"), GlobalPos.of(Level.END, new BlockPos(500, 60, 0))
				));
				server.runCommand("gamemode survival Tester");
				server.runCommand("execute in minecraft:the_end run tp Tester 540.5 40 0.5 90 10");
				double caughtAt = Double.NaN;
				double lowest = Double.POSITIVE_INFINITY;
				boolean rescued = false;
				double whitest = 0;
				int exitRift = 0;
				for (int frame = 0; frame < 40; frame++) {
					ctx.waitTicks(2);
					double[] state = server.computeOnServer(s -> {
						var p = s.getPlayerList().getPlayerByName("Tester");
						return new double[]{p.getY(), p.getXRot(), RiftTracker.isRifting(p.getUUID()) ? 1 : 0};
					});
					System.out.printf("[qol-clienttest] fall %d: y=%.1f pitch=%.0f rifting=%s%n", frame, state[0], state[1], state[2] > 0);
					BufferedImage shot = read(ctx.takeScreenshot(TestScreenshotOptions.of("fall_" + (frame < 10 ? "0" : "") + frame).withSize(960, 540)));
					// the white-out: most of the screen near white
					whitest = Math.max(whitest, countAll(shot, (r, g, bl) -> r > 225 && g > 225 && bl > 225) / (double) (shot.getWidth() * shot.getHeight()));
					if (state[0] > 55) {
						// home: the way out stands on the anchor in front of them
						exitRift = Math.max(exitRift, countAll(shot, (r, g, bl) -> (bl > g + 40 && r > g + 20) || (r > 200 && bl > 200 && g < r - 10)));
					}
					if (state[2] > 0) {
						if (Double.isNaN(caughtAt)) caughtAt = state[0];
						lowest = Math.min(lowest, state[0]);
					}
					rescued |= state[0] > 55;
				}
				// Caught, the player hangs and sinks into the rift (about 6 blocks below), not on past it.
				if (Double.isNaN(caughtAt)) throw new AssertionError("the falling player was never caught");
				if (lowest < caughtAt - 9) throw new AssertionError("fell on past the rift: caught at y " + caughtAt + ", fell to " + lowest);
				if (!rescued) throw new AssertionError("never taken to the anchor");
				System.out.printf("[qol-clienttest] whitest frame %.2f, exit rift %d px%n", whitest, exitRift);
				if (whitest < 0.6) throw new AssertionError("the screen never whited out: at most " + whitest + " white");
				if (exitRift < 2000) throw new AssertionError("no rift on the anchor to step out of: " + exitRift + " rift pixels");
				server.runCommand("gamemode spectator Tester");
				server.runCommand("execute in minecraft:overworld run tp Tester 0.5 -58.4 2.0 0 25");

				// The block at each charge, in a row in front of the camera.
				for (int charge = 0; charge <= 4; charge++) {
					server.runCommand("setblock " + (charge * 2 - 4) + " -60 6 metacraft:void_anchor[charges=" + charge + "]");
				}
				server.runCommand("tp Tester 0.5 -58.4 2.0 0 25");
				ctx.waitTicks(40);
				ctx.takeScreenshot(TestScreenshotOptions.of("anchors").withSize(1920, 1080));
			}
		}
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

	/** Pixels anywhere on the screen that match. */
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

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}

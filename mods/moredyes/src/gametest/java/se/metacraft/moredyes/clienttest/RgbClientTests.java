package se.metacraft.moredyes.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConfirmScreen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * RGB drifts on a vanilla client. Each thing that should change colour stands in front of a
 * white wall; the camera looks at each in turn, then again a quarter of the loop later (900 ticks,
 * about 90 degrees of hue), and the mean hue of each shot is compared.
 *
 * Only the wool is asserted. The others are the places the design could not be sure of (the shulker
 * item and banners draw from atlases other than the block atlas) or expects to hold still (the
 * llama carpet and the happy ghast harness are equipment textures, which no atlas animates), so their
 * hue change is printed for a person to read. Look at the screenshots too.
 *
 * Run: {@code ./gradlew :mods:moredyes:runClientGameTest}. Screenshots land in
 * {@code mods/moredyes/build/run/clientGameTest/screenshots}.
 */
public final class RgbClientTests implements FabricClientGameTest {
	/** What the camera looks at: where from, and at what. */
	private record Subject(String name, double x, double y, double z, double lookX, double lookY, double lookZ) {
		/** Camera 5 blocks in front of a thing at (x, y, z) on the line z = 6, looking at it. */
		static Subject at(String name, double x, double y, double distance) {
			return new Subject(name, x, y + 0.5, 6 - distance, x, y, 6);
		}
	}

	private static final int FLOOR = -61;
	private static final int Y = -60;

	private static final List<Subject> SUBJECTS = List.of(
			Subject.at("wool", 0.5, Y + 1.5, 5),
			Subject.at("concrete", 6.5, Y + 1.5, 5),
			Subject.at("glass", 12.5, Y + 1.5, 5),
			Subject.at("shulker_item", 18.5, Y + 1.4, 1),
			Subject.at("banner", 24.5, Y + 1.2, 2.5),
			Subject.at("armour_stand", 30.5, Y + 1.2, 3),
			Subject.at("llama_carpet", 36.5, Y + 0.9, 4),
			Subject.at("ghast_harness", 44.5, Y + 3.5, 9),
			// the whole row at once, from far enough back to fit all of it
			new Subject("overview", 22.5, Y + 5, 6 - 24, 22.5, Y + 1.5, 6)
	);

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25604");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		props.setProperty("view-distance", "12");
		props.setProperty("simulation-distance", "12");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.waitTicks(20);
				ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
				ctx.waitTicks(20);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("gamemode spectator Tester");
				ctx.runOnClient(client -> {
					client.options.setCameraType(CameraType.FIRST_PERSON);
					client.options.renderDistance().set(12);
					client.options.fov().set(70);
				});
				scene(server);
				ctx.waitTicks(100);   // everything spawned and drawn before the first shot

				List<Double> before = pass(ctx, server, conn, "t0");
				List<Long> beforeTick = ticks;
				ctx.waitTicks(700);
				List<Double> after = pass(ctx, server, conn, "t1");

				System.out.println("[moredyes-clienttest] RGB hue per region (degrees, mean of the coloured pixels in each shot):");
				double wool = Double.NaN;
				for (int i = 0; i < SUBJECTS.size(); i++) {
					double moved = hueDelta(before.get(i), after.get(i));
					System.out.printf("[moredyes-clienttest]   %-14s %6.1f -> %6.1f   moved %6.1f degrees over %d ticks%n",
							SUBJECTS.get(i).name(), before.get(i), after.get(i), moved, tickAfter.get(i) - beforeTick.get(i));
					if (SUBJECTS.get(i).name().equals("wool")) wool = moved;
				}
				if (Double.isNaN(wool) || Math.abs(wool) < 20) {
					throw new AssertionError("the RGB wool's hue moved " + wool + " degrees in about 800 ticks; expected at least 20");
				}
			}
		}
	}

	/** The row of things, on a stone floor in front of a white wall. */
	private static void scene(TestDedicatedServerContext server) {
		server.runCommand("fill -8 " + FLOOR + " -6 56 " + FLOOR + " 12 minecraft:stone");
		server.runCommand("fill -8 " + Y + " 8 56 -50 8 minecraft:white_concrete");
		// wool, concrete, glass: a 3x3 wall each, glass on the white wall so its tint reads
		server.runCommand("fill -1 " + Y + " 7 1 " + (Y + 2) + " 7 moredyes:rgb_wool");
		server.runCommand("fill 5 " + Y + " 7 7 " + (Y + 2) + " 7 moredyes:rgb_concrete");
		server.runCommand("fill 11 " + Y + " 7 13 " + (Y + 2) + " 7 moredyes:rgb_stained_glass");
		// a shulker box in an invisible item frame on the wall
		server.runCommand("summon item_frame 18 " + (Y + 1) + " 7 {Facing:2b,Invisible:1b,Invulnerable:1b,Item:{id:\"moredyes:rgb_shulker_box\",count:1}}");
		// a banner on the wall, in the RGB colour
		server.runCommand("setblock 24 " + (Y + 1) + " 7 minecraft:white_wall_banner[facing=north]");
		server.runCommand("data merge block 24 " + (Y + 1) + " 7 {patterns:[{pattern:\"moredyes:rgb/minecraft/base\",color:\"white\"}]}");
		// worn: leather armour (the marker is the colour id; the colour is RGB's own #FF4040), a carpet, a harness
		server.runCommand("summon armor_stand 30.5 " + Y + " 5 {Invulnerable:1b,Rotation:[180f,0f],equipment:{chest:{id:\"minecraft:leather_chestplate\","
				+ "count:1,components:{\"minecraft:dyed_color\":16728128,\"moredyes:animated_dye\":\"rgb\"}}}}");
		server.runCommand("summon llama 36.5 " + Y + " 5 {NoAI:1b,Silent:1b,Rotation:[180f,0f],equipment:{body:{id:\"moredyes:rgb_carpet\",count:1}}}");
		server.runCommand("summon happy_ghast 44.5 " + (Y + 2) + " 4 {NoAI:1b,Silent:1b,Rotation:[180f,0f],equipment:{body:{id:\"moredyes:rgb_harness\",count:1}}}");
	}

	/** The client's game time at each subject's shot, per pass. */
	private static List<Long> ticks = new ArrayList<>();
	private static List<Long> tickAfter = new ArrayList<>();

	/** One shot of each subject; the mean hue of each. */
	private static List<Double> pass(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, String phase) {
		List<Double> hues = new ArrayList<>();
		List<Long> at = new ArrayList<>();
		for (Subject s : SUBJECTS) {
			server.runCommand(String.format("tp Tester %.2f %.2f %.2f facing %.2f %.2f %.2f", s.x(), s.y(), s.z(), s.lookX(), s.lookY(), s.lookZ()));
			conn.waitForClientboundPackets();
			ctx.waitTicks(20);
			at.add(ctx.computeOnClient(client -> client.level.getGameTime()));
			Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("rgb_" + s.name() + "_" + phase).withSize(1920, 1080));
			System.out.println("[moredyes-clienttest] screenshot: " + shot);
			hues.add(meanHue(shot));
		}
		if (phase.equals("t0")) ticks = at; else tickAfter = at;
		return hues;
	}

	/** Mean hue in degrees of the coloured pixels in the image (the wall and floor are grey, so only the subject and its neighbours count); NaN if there are none. */
	private static double meanHue(Path png) {
		BufferedImage img;
		try {
			img = ImageIO.read(png.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		int w = img.getWidth(), h = img.getHeight();
		double x = 0, y = 0;
		int n = 0;
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int p = img.getRGB(px, py);
				float[] hsb = java.awt.Color.RGBtoHSB(p >> 16 & 0xFF, p >> 8 & 0xFF, p & 0xFF, null);
				if (hsb[1] < 0.3f || hsb[2] < 0.2f) continue;   // white wall, stone, shadow: no hue to speak of
				x += Math.cos(hsb[0] * 2 * Math.PI);
				y += Math.sin(hsb[0] * 2 * Math.PI);
				n++;
			}
		}
		if (n < 20) return Double.NaN;
		return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
	}

	/** Signed shortest change from one hue to another, in degrees. */
	private static double hueDelta(double from, double to) {
		if (Double.isNaN(from) || Double.isNaN(to)) return Double.NaN;
		return ((to - from + 540) % 360) - 180;
	}
}

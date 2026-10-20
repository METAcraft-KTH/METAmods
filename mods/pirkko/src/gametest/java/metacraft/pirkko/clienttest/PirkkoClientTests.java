package metacraft.pirkko.clienttest;

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
import java.nio.file.Path;
import java.util.Properties;

/**
 * What a vanilla client sees of the shader-rendered Pirkkos (player heads whose skin the entity
 * shader draws as a ray-traced figure). A dedicated server runs in-process with pirkko; the test
 * client joins it as a vanilla client (PolymerHelloMixin), accepts the pack, and looks at floor and
 * wall heads in front of a white wall. Each head is a different colour, so a screenshot proves each
 * one was drawn as a figure; the Steve skin those heads carry must not show anywhere, which it
 * would if the shader drew them as ordinary heads.
 *
 * Run: {@code ./gradlew :mods:pirkko:runClientGameTest} (opens a window). Screenshots land in
 * {@code build/run/clientGameTest/screenshots}.
 */
public final class PirkkoClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25598");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("gamerule send_command_feedback false");   // no chat lines over the shot
				server.runCommand("time set noon");
				// A white floor and wall, so the figures' colours are the only colours in view.
				server.runCommand("fill -4 -61 0 4 -61 5 minecraft:white_concrete");
				server.runCommand("fill -4 -60 4 4 -56 4 minecraft:white_concrete");
				// Floor heads: green, rainbow, cerise.
				head(server, -1, -60, 2, "minecraft:player_head[rotation=8]", "marker_green");
				head(server, 0, -60, 2, "minecraft:player_head[rotation=8]", "marker_rainbow");
				head(server, 1, -60, 2, "minecraft:player_head[rotation=8]", "marker_cerise");
				// Wall heads, which the server sends with their _wall skin: laserviolett and red.
				head(server, -2, -59, 3, "minecraft:player_wall_head[facing=north]", "marker_laserviolett");
				head(server, 2, -59, 3, "minecraft:player_wall_head[facing=north]", "pirkko_marker");
				// A spectator has no hand (the arm is a skin too) and no hearts or hotbar in view.
				server.runCommand("gamemode spectator Tester");
				server.runCommand("tp Tester 0.5 -60 -0.5 0 15");   // south, looking down at the heads
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(220);   // until the join toasts and the chat have faded
				server.runCommand("tick freeze");   // the rainbow Pirkko's colour follows the game time
				conn.waitForClientboundPackets();
				ctx.waitTicks(5);

				BufferedImage shot = read(ctx.takeScreenshot(TestScreenshotOptions.of("pirkko_heads").withSize(1920, 1080)));
				assertPresent(shot, "the green floor Pirkko", (r, g, b) -> g > 90 && g > r + 60 && g > b + 60, 200);
				assertPresent(shot, "the cerise floor Pirkko", (r, g, b) -> r > 100 && r > g + 60 && b > g + 25, 200);
				assertPresent(shot, "the laserviolett wall Pirkko", (r, g, b) -> b > 120 && b > g + 40 && r > g + 15, 200);
				assertPresent(shot, "the red wall Pirkko", (r, g, b) -> r > 90 && r > g + 60 && Math.abs(g - b) < 25, 200);
				// Steve's face colours keep their ratios under any light; nothing else in view has them.
				int steve = count(shot, (r, g, b) -> r > 50 && g > 0.58 * r && g < 0.72 * r && b > 0.42 * r && b < 0.58 * r);
				if (steve > 20) throw new AssertionError(steve + " pixel(s) of Steve's skin in view: a head was drawn as a head, not as a Pirkko");
				server.runCommand("tick unfreeze");
			}
		}
	}

	private static void head(TestDedicatedServerContext server, int x, int y, int z, String block, String skin) {
		server.runCommand("setblock " + x + " " + y + " " + z + " " + block + "{profile:{texture:\"pirkko:entity/" + skin + "\"}}");
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}

	interface Rgb { boolean test(int r, int g, int b); }

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}

	private static int count(BufferedImage img, Rgb match) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y);
				if (match.test((p >> 16) & 255, (p >> 8) & 255, p & 255)) n++;
			}
		}
		return n;
	}

	private static void assertPresent(BufferedImage img, String what, Rgb match, int atLeast) {
		int n = count(img, match);
		if (n < atLeast) throw new AssertionError(what + ": " + n + " matching pixel(s) in view, expected at least " + atLeast);
	}
}

package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.server.packs.repository.PackRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Chapter text colours on nametags: with the chapter-text-colors resource pack on (and, under {@code -Piris}, the
 * shaderpack patched by ovvar-client), a dark purple nametag must draw like one in cerise and a dark blue one like
 * one in laserviolet. Runs only with {@code -PtextColours} (it puts the test copy of the pack in the run dir and sets
 * {@code -Dovvar.textColours}); see the README.
 *
 * <p>The judgement is relative, never against fixed RGB: a shaderpack lights, fogs and tone-maps nametag text (BSL
 * draws it pale and sky-tinted), so no absolute colour is right under every pack. Each chapter tag is compared with
 * two reference tags drawn the same way at the same spot: the colour it should become ({@code #E83D84},
 * {@code #CC99FF}, which no patch touches) and a colour one step off its stock one ({@code #AB00AB}, {@code #0000AB}:
 * where it would land unchanged, since the exact match leaves these alone). It must be much nearer the first. A white
 * wall behind the tags keeps the translucent plate's background still (no clouds drifting through it).
 *
 * <p>The measure is {@link ShotMeasures#nametag} (pinned on real BSL frames by ShotMeasuresTest): the plate is the
 * connected region that changed from the frame without a tag, spans the centre and darkens the wall (a toast that
 * slides in is another region); the glyph colour is the median of the plate's pixels farthest from its own colour;
 * colours are compared by chroma, not brightness, since BSL dims and tints the glyphs.
 */
public final class NametagColoursClientTests implements FabricClientGameTest {
	/** The cases: the chapter colour, what it should draw like, and its stock colour one step off. */
	private static final List<String[]> CASES = List.of(
			new String[]{"dark_purple", "#E83D84", "#AB00AB"},
			new String[]{"dark_blue", "#CC99FF", "#0000AB"});

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String pack = System.getProperty("ovvar.textColours");
		if (pack == null || pack.isEmpty()) return;
		String packId = "file/" + pack;
		ctx.runOnClient(client -> {
			PackRepository repo = client.getResourcePackRepository();
			repo.reload();
			if (!repo.isAvailable(packId)) throw new AssertionError("the resource pack " + packId + " is not in the run dir (run with -PtextColours)");
			repo.addPack(packId);
			client.options.updateResourcePacks(repo);
		});
		ctx.waitFor(client -> client.gui.overlay() == null, 20 * 60);
		ctx.waitTicks(20);
		ctx.runOnClient(client -> {
			System.out.println("[ovvar-clienttest] text colours: packs " + client.getResourcePackRepository().getSelectedIds()
					+ ", shaderpack " + System.getProperty("ovvar.iris.pack", "none"));
			if (!client.getResourcePackRepository().getSelectedIds().contains(packId)) throw new AssertionError(packId + " did not stay selected");
		});
		try {
			measure(ctx);
		} finally {
			// the tests after this one draw text too (lore colours): leave the client's packs as they were
			ctx.runOnClient(client -> {
				PackRepository repo = client.getResourcePackRepository();
				repo.removePack(packId);
				client.options.updateResourcePacks(repo);
			});
			ctx.waitFor(client -> client.gui.overlay() == null, 20 * 60);
		}
	}

	private static void measure(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25597");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ClientTestSupport.acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("gamerule advance_weather false");
				server.runCommand("weather clear");
				server.runCommand("time set noon");
				server.runCommand("fill -6 -61 7 7 -52 7 minecraft:white_concrete");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				BufferedImage base = shot(ctx, "text_colours_base");
				for (String[] c : CASES) {
					BufferedImage chapter = tag(ctx, server, c[0]), target = tag(ctx, server, c[1]), stock = tag(ctx, server, c[2]);
					ShotMeasures.Nametag n = ShotMeasures.nametag(base, chapter, target, stock, middle(base));
					System.out.println("[ovvar-clienttest] text colours: " + c[0] + " like " + c[1] + ", not " + c[2] + ": " + n);
					String failure = n.failure();
					if (failure != null) throw new AssertionError("a " + c[0] + " nametag (" + c[1] + " vs stock " + c[2] + "): " + failure + "; " + n);
				}
				server.runCommand("kill @e[type=armor_stand]");
			}
		}
	}

	/** A frame with a nametag in {@code colour} (a name or #RRGGBB) on an invisible armour stand three blocks ahead. */
	private static BufferedImage tag(ClientGameTestContext ctx, TestDedicatedServerContext server, String colour) {
		server.runCommand("kill @e[type=armor_stand]");
		server.runCommand("summon armor_stand 0.5 -60 3.5 {Invisible:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:{text:\"Tester2\",color:\""
				+ colour + "\"}}");
		ctx.waitTicks(30);
		return shot(ctx, "text_colours_" + colour.replace("#", "hex_"));
	}

	/** Where the tag is searched: the middle of the frame (it sits above its centre, on the wall), clear of the sky and the hand. */
	private static ShotMeasures.Rect middle(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		return new ShotMeasures.Rect(w * 3 / 10, h / 8, w * 7 / 10, h * 5 / 8);
	}

	private static BufferedImage shot(ClientGameTestContext ctx, String name) {
		Path path = ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080));
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}
}

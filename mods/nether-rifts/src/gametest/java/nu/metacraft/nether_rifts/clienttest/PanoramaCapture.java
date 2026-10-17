package nu.metacraft.nether_rifts.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Captures the nether panorama the rift shows through its cracks (tools/panorama, then tools/gen_panorama.py).
 * Runs only with -Dnether_rifts.capturePanorama=true (./gradlew :mods:nether-rifts:runClientGameTest
 * -PcapturePanorama -PpanoramaWorld=...); otherwise it does nothing.
 *
 * The scene is Eskmorra, a nether fortress build by Bluespartan, zombiespartan456 and shiiiner (see the module
 * README for the source and its terms). It isn't in the repo: nether_rifts.panoramaWorld names the downloaded
 * world's region folder, whose files are copied into the test server's nether before anything loads there. The
 * camera floats over its lava sea at {@link #SPOT}, high enough that each side's piece sits mid-face, where a rift
 * seen head on looks: the tower to the south, the bridge below to the west, the hall up on its island to the north,
 * the serpent's head to the east. nether_rifts.panoramaSpots ("x,y,z;x,y,z")
 * tries other spots instead (more than one go to numbered folders).
 */
public final class PanoramaCapture implements FabricClientGameTest {

	static final boolean CAPTURING = Boolean.getBoolean("nether_rifts.capturePanorama");
	private static final String SEED = "6202610";
	private static final String SPOT = "394,42,672";
	// the build and the lava sea round it; the 2014 world's chunks carry overworld biomes, whose fog isn't the nether's
	private static final int[] AREA = {272, 480, 591, 767};
	private static final int RENDER_DISTANCE = 8;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!CAPTURING) {
			return;
		}
		Path out = Path.of(System.getProperty("nether_rifts.panoramaDir", "panorama")).toAbsolutePath();
		String world = System.getProperty("nether_rifts.panoramaWorld");
		if (world == null || world.isBlank()) {
			throw new AssertionError("no -PpanoramaWorld: the region folder of the downloaded Eskmorra world (README, 'The panorama')");
		}
		String[] spots = System.getProperty("nether_rifts.panoramaSpots", SPOT).split(";");
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", String.valueOf(RiftClientTests.freePort()));
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().adjustSettings(w -> w.setSeed(SEED)).createServer(props)) {
			// region files are read when a chunk in them first loads, and nothing in the nether has yet
			Path regions = server.computeOnServer(s -> DimensionType.getStorageFolder(Level.NETHER, s.getWorldPath(LevelResource.ROOT)).resolve("region"));
			copyRegions(Path.of(world), regions);
			// forceload takes 256 chunks at most: the area in two halves
			int midX = (AREA[0] + AREA[2]) / 2;
			server.runCommand("gamerule max_block_modifications 100000000");
			server.runCommand("execute in minecraft:the_nether run forceload add " + AREA[0] + " " + AREA[1] + " " + midX + " " + AREA[3]);
			server.runCommand("execute in minecraft:the_nether run forceload add " + (midX + 1) + " " + AREA[1] + " " + AREA[2] + " " + AREA[3]);
			server.runCommand("execute in minecraft:the_nether run fillbiome " + AREA[0] + " 0 " + AREA[1] + " " + AREA[2] + " 255 " + AREA[3] + " minecraft:nether_wastes");
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.waitTicks(20);
				ctx.waitFor(client -> client.gui.overlay() == null, 20 * 90);
				// the whole build in view, and brightness up: through a thin crack the nether's shadows would read as black
				ctx.runOnClient(client -> {
					client.options.renderDistance().set(RENDER_DISTANCE);
					client.options.gamma().set(1.0);
					client.options.broadcastOptions();
				});
				server.runCommand("gamemode spectator Tester");
				for (int i = 0; i < spots.length; i++) {
					String[] xyz = spots[i].trim().split(",");
					System.out.println("[nether-rifts-clienttest] panorama at " + String.join(" ", xyz));
					capture(ctx, server, conn, xyz, spots.length == 1 ? out : out.resolve("spot-" + i));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Waits for the sections in view to be built. Not waitForChunksRender: it wants every chunk in the square of the
	 * render distance, and the server only sends a circle, so it never ends at this distance.
	 */
	private static void settle(ClientGameTestContext ctx) {
		ctx.waitTicks(40);
		ctx.waitFor(client -> client.levelRenderer.hasRenderedAllSections(), 20 * 60);
	}

	private static void copyRegions(Path from, Path to) throws IOException {
		Files.createDirectories(to);
		int n = 0;
		try (Stream<Path> files = Files.list(from)) {
			for (Path f : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".mca"))::iterator) {
				Files.copy(f, to.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				n++;
			}
		}
		if (n == 0) {
			throw new AssertionError("no region files (*.mca) in " + from);
		}
		System.out.println("[nether-rifts-clienttest] copied " + n + " region files from " + from);
	}

	private static void capture(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, String[] xyz, Path out) throws IOException {
		server.runCommand("execute in minecraft:the_nether run tp Tester " + xyz[0] + ".5 " + xyz[1] + " " + xyz[2] + ".5 0 0");
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		// sections are only built once seen: look every way first, then take the panorama twice, keeping the second
		float[][] looks = {{0, 0}, {90, 0}, {180, 0}, {270, 0}, {0, -90}, {0, 90}};
		for (float[] look : looks) {
			ctx.runOnClient(client -> {
				client.player.setYRot(look[0]);
				client.player.setXRot(look[1]);
			});
			ctx.waitTicks(30);
		}
		ctx.runOnClient(client -> {
			client.player.setYRot(0);
			client.player.setXRot(0);
		});
		settle(ctx);
		ctx.waitTicks(60);
		Path warmUp = Files.createTempDirectory("nether-rifts-panorama-warmup");
		ctx.runOnClient(client -> client.grabPanoramixScreenshot(warmUp.toFile()));
		ctx.waitTicks(100);
		settle(ctx);
		Path folder = Files.createTempDirectory("nether-rifts-panorama");
		ctx.runOnClient(client -> client.grabPanoramixScreenshot(folder.toFile()));
		ctx.waitTicks(40);
		Files.createDirectories(out);
		for (int i = 0; i < 6; i++) {
			Path face = folder.resolve("screenshots/panorama_" + i + ".png");
			ctx.waitFor(client -> Files.exists(face), 20 * 30);
			ctx.waitTicks(10);
			Files.copy(face, out.resolve("panorama_" + i + ".png"), StandardCopyOption.REPLACE_EXISTING);
		}
		System.out.println("[nether-rifts-clienttest] panorama written to " + out);
	}
}

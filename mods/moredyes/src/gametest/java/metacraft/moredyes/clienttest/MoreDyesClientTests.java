package metacraft.moredyes.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;

import java.nio.file.Path;
import java.util.Properties;

/**
 * What a vanilla client sees of our stairs and slabs. A dedicated server runs in-process with the
 * mod; the client joins it as a vanilla client (PolymerHelloMixin) and accepts the pack.
 *
 * The scene is a row of stairs and slabs in both colours and both materials on a stone floor, each
 * with a stone block behind it (an invisible stair hid that block's face and the floor under it: the
 * x-ray), and vanilla's stairs in the colours ours fall back to beside them for comparison. Shots:
 * close up with the crosshair on a stair (the selection outline, and no bulge); from 30 blocks with a
 * narrow field of view (the displays are drawn: no z-fighting with the vanilla faces under them), and
 * from 55; from 100 blocks (past the displays' 64-block view range: the vanilla stairs, no holes); and
 * the first frame the client has a stair that was just placed. The screenshots are judged by eye: open them and look.
 *
 * Run: {@code ./gradlew :mods:moredyes:runClientGameTest} (opens a window). Screenshots land in
 * {@code mods/moredyes/build/run/clientGameTest/screenshots}.
 */
public final class MoreDyesClientTests implements FabricClientGameTest {
	/** The row, west to east, at z = 6 on the floor (y = -60). */
	private static final String[] ROW = {
			"moredyes:cerise_wool_stairs", "moredyes:cerise_concrete_stairs", "moredyes:cerise_wool_slab", "moredyes:cerise_concrete_slab",
			"minecraft:pink_wool_stairs",
			"moredyes:laserviolet_wool_stairs", "moredyes:laserviolet_concrete_stairs", "moredyes:laserviolet_wool_slab", "moredyes:laserviolet_concrete_slab",
			"minecraft:light_gray_wool_stairs",
	};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25603");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		props.setProperty("view-distance", "12");
		props.setProperty("simulation-distance", "12");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("gamemode spectator Tester");
				ctx.runOnClient(client -> {
					client.options.setCameraType(CameraType.FIRST_PERSON);
					client.options.renderDistance().set(12);
				});
				// stone floor and a stone wall behind the row, one block back
				server.runCommand("fill -8 -61 3 14 -61 9 minecraft:stone");
				server.runCommand("fill -8 -60 7 14 -58 7 minecraft:stone");
				for (int i = 0; i < ROW.length; i++) {
					String variant = ROW[i].endsWith("_stairs") ? "[facing=north]" : "[type=bottom]";
					server.runCommand("setblock " + (2 * i - 6) + " -60 6 " + ROW[i] + variant);
					// a top slab and an upside-down stair one up, against the wall
					server.runCommand("setblock " + (2 * i - 6) + " -59 6 " + ROW[i] + (ROW[i].endsWith("_stairs") ? "[facing=east,half=top]" : "[type=top]"));
				}
				// two stairs side by side, for the seam
				server.runCommand("setblock 13 -60 6 moredyes:cerise_wool_stairs[facing=north]");
				server.runCommand("setblock 14 -60 6 moredyes:laserviolet_wool_stairs[facing=north]");

				// Close up, standing (a spectator gets no selection outline), the crosshair on the first
				// Cerise wool stair.
				server.runCommand("gamemode creative Tester");
				server.runCommand("tp Tester -5.5 -60 3.5 0 23");
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				shot(ctx, "stairs_close");

				// The whole row from the front, normal field of view.
				server.runCommand("gamemode spectator Tester");
				server.runCommand("tp Tester 4 -57.5 -2 0 20");
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				shot(ctx, "stairs_row");

				// Zoomed in from 30 and 55 blocks: displays drawn, over the vanilla faces (z-fighting would
				// show as speckles of the vanilla colour). Then 100 blocks: past the displays' view range.
				ctx.runOnClient(client -> client.options.fov().set(30));
				server.runCommand("tp Tester 4 -52 -24 0 14");
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				shot(ctx, "stairs_30_blocks");
				ctx.runOnClient(client -> client.options.fov().set(30));
				server.runCommand("tp Tester 4 -50 -49 0 10");
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				shot(ctx, "stairs_55_blocks");
				ctx.runOnClient(client -> client.options.fov().set(30));
				server.runCommand("tp Tester 4 -40 -94 0 11");
				conn.waitForClientboundPackets();
				ctx.waitTicks(200);   // the chunks out there to arrive and build
				shot(ctx, "stairs_100_blocks");

				// Placing: a stair set next to the wall with the camera close, one tick later.
				ctx.runOnClient(client -> client.options.fov().set(70));
				server.runCommand("tp Tester 10.5 -58.5 2.5 0 40");
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				server.runCommand("setblock 11 -60 4 minecraft:stone");
				ctx.waitTicks(10);
				// The first frame the client has the block: whether its display has arrived yet is printed.
				BlockPos placed = new BlockPos(10, -60, 4);
				server.runCommand("setblock 10 -60 4 moredyes:cerise_concrete_stairs[facing=south]");
				ctx.waitFor(client -> client.level.getBlockState(placed).getBlock() instanceof StairBlock, 100);
				int displays = ctx.computeOnClient(client -> client.level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(placed).inflate(0.5)).size());
				System.out.println("[moredyes-clienttest] first frame with the placed stair: " + displays + " display(s) there");
				shot(ctx, "stairs_placing");
				ctx.waitTicks(20);
				shot(ctx, "stairs_placed");
			}
		}
	}

	private static void shot(ClientGameTestContext ctx, String name) {
		Path shot = ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080));
		System.out.println("[moredyes-clienttest] screenshot: " + shot);
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}

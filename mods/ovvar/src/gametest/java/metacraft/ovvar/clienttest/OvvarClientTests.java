package metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.component.DataComponents;
import metacraft.ovvar.content.ModComponents;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.EntityTypes;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.SpotPlacements;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.Chapter;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

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
				});
				acceptResourcePack(ctx);   // the current system may push a rebuilt pack for the fourth patch
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
				assertPresent(a, box, "itk (green)", (r, g, bl) -> g > 100 && g > r + 60 && bl < 140, 20);
				assertPresent(a, box, "it (lilac rim)", (r, g, bl) -> bl > 150 && bl > r + 25 && r > g + 20, 20);
				// The garment surface must be steady between two frames: the prototype's tag crawl changed
				// interior pixels by large amounts every frame, which this catches. A few silhouette pixels
				// shift by sub-pixel model interpolation (small deltas); those are tolerated.
				int flicker = flickering(a, b, box, 24);
				int budget = (box.x1() - box.x0()) * (box.y1() - box.y0()) / 100;   // 1% for residual edge motion
				if (flicker > budget) throw new AssertionError(flicker + " pixel(s) over the mannequin flickered by more than a step between two still frames (budget " + budget + ")");
			}
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
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

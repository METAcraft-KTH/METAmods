package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.item.OvveItem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Under an Iris shaderpack: an unpatched pack draws the magenta/black checker that fills the original
 * right-limb box of a garment texture (the ovvar shader reads the copy beside it), a pack patched by
 * OvvarShaderPatcher does not. Runs only with {@code -Dovvar.iris.expect=checker|art} (see the README,
 * "Client test under a shaderpack"); otherwise it does nothing and the vanilla client run is unchanged.
 */
public final class OvvarShaderpackClientTests implements FabricClientGameTest {
	/** #FF00FF, the Spot.CHECKER_A colour. */
	private static final int MAGENTA_R = 255, MAGENTA_G = 0, MAGENTA_B = 255;
	private static final int TOLERANCE = 40;
	private static final double CHECKER_SHARE = 0.30;
	private static final double GARMENT_SHARE = 0.50;
	/**
	 * The Data ovve cloth's dominant texel, rgb(189,55,84): hue about 345 degrees, apart from skin (20-35) and
	 * from the checker's magenta (300). A bare skin leg, or a leg showing the checker, has too few such pixels.
	 */
	private static final float DATA_CLOTH_HUE = java.awt.Color.RGBtoHSB(189, 55, 84, null)[0];
	private static final float HUE_RANGE = 25f / 360f;
	private static final float MIN_SATURATION = 0.3f;
	private static final double CLOTH_SHARE = 0.30;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String expect = System.getProperty("ovvar.iris.expect");
		if (expect == null || expect.isEmpty()) return;
		if (!expect.equals("checker") && !expect.equals("art")) {
			throw new AssertionError("ovvar.iris.expect must be checker or art, not " + expect);
		}
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25598");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ClientTestSupport.acceptResourcePack(ctx);
				conn.waitForChunksRender();
				ctx.runOnClient(client -> assertIrisLoadedPack());
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				server.runOnServer(mc -> {
					ServerLevel level = mc.getPlayerList().getPlayers().getFirst().level();
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					OvveItem.setTopUp(ovve, true);   // nothing sewn: the bare garment
					Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
					m.setPos(0.5, -60, 3.5);
					m.setYRot(180); m.setYBodyRot(180); m.setYHeadRot(180);
					m.setItemSlot(EquipmentSlot.LEGS, ovve);
					ItemStack top = new ItemStack(ModContent.top(Chapter.DATA));
					top.set(ModComponents.PATCHES, ovve.get(ModComponents.PATCHES));
					m.setItemSlot(EquipmentSlot.CHEST, top);
					level.addFreshEntity(m);
				});
				ClientTestSupport.acceptResourcePack(ctx);   // the pack, once the ovve exists
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);
				ctx.runOnClient(client -> assertIrisLoadedPack());
				Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("ovvar_iris_leg_" + expect).withSize(1920, 1080));
				BufferedImage img = read(shot);
				// The mannequin faces the camera, so its right leg is on the screen's left of centre: the
				// 4-model-pixel-wide column ending at the middle, in the lower part of the subject box
				// (the box is calibrated in OvvarClientTests: ~19 screen px per model pixel).
				OvvarClientTests.Region box = OvvarClientTests.Region.subjectBox(img);
				int cx = img.getWidth() / 2;
				int x0 = cx - 66, x1 = cx - 8, y0 = box.y1() - 85, y1 = box.y1() - 10;
				int near = 0, total = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int p = img.getRGB(x, y);
						total++;
						if (Math.abs(((p >> 16) & 255) - MAGENTA_R) <= TOLERANCE
								&& Math.abs(((p >> 8) & 255) - MAGENTA_G) <= TOLERANCE
								&& Math.abs((p & 255) - MAGENTA_B) <= TOLERANCE) near++;
					}
				}
				// Positive control: the crop must be mostly garment, or "art" passes on a crop that missed the leg
				// (or a garment that never drew). Garment = differs from the background beside the subject, same row.
				int garment = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int p = img.getRGB(x, y), q = img.getRGB(x + 300, y);   // 300 px right: well clear of the arm (centre +150)
						if (Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) > TOLERANCE
								|| Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) > TOLERANCE
								|| Math.abs((p & 255) - (q & 255)) > TOLERANCE) garment++;
					}
				}
				System.out.println("[ovvar-clienttest] iris " + expect + ": " + garment + "/" + total + " crop pixels differ from the background");
				if (garment < total * GARMENT_SHARE) {
					throw new AssertionError("the right-leg crop is not on the garment: only " + garment + "/" + total + " pixels differ from the background");
				}
				int cloth = 0;
				float[] hsb = new float[3];
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int p = img.getRGB(x, y);
						java.awt.Color.RGBtoHSB((p >> 16) & 255, (p >> 8) & 255, p & 255, hsb);
						float dh = Math.abs(hsb[0] - DATA_CLOTH_HUE);
						if (hsb[1] >= MIN_SATURATION && Math.min(dh, 1f - dh) <= HUE_RANGE) cloth++;
					}
				}
				System.out.println("[ovvar-clienttest] iris " + expect + ": " + cloth + "/" + total + " crop pixels are Data cloth coloured");
				if (expect.equals("art") && cloth < total * CLOTH_SHARE) {
					throw new AssertionError("the right-leg crop does not show the Data ovve's cloth: only " + cloth + "/" + total + " cerise pixels (a bare leg or an ovve that never drew)");
				}
				double share = near / (double) total;
				System.out.println("[ovvar-clienttest] iris " + expect + ": " + near + "/" + total + " right-leg pixels near #FF00FF (" + share + ")");
				if (expect.equals("checker") && share < CHECKER_SHARE) {
					throw new AssertionError("expected the unpatched pack's checker on the right leg: only " + near + "/" + total + " pixels near #FF00FF");
				}
				if (expect.equals("art") && near > 0) {
					throw new AssertionError("expected no checker on the right leg of a patched pack: " + near + "/" + total + " pixels near #FF00FF");
				}
			}
		}
	}

	/** Iris falls back silently when a pack fails to load, so ask it: a pack in use, and the one -Piris named. */
	private static void assertIrisLoadedPack() {
		String want = System.getProperty("ovvar.iris.pack");
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			boolean inUse = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
			Object name = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
			System.out.println("[ovvar-clienttest] iris pack in use=" + inUse + " name=" + name + " wanted=" + want);
			if (!inUse) throw new AssertionError("Iris is not running a shaderpack (it falls back silently when one fails to load)");
			if (want != null && (name == null || !want.replaceAll("\\.zip$", "").equals(name.toString().replaceAll("\\.zip$", "")))) {
				throw new AssertionError("Iris runs the pack " + name + ", not " + want);
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Iris is not on the test client's classpath (run with -Piris=<pack.zip>)", e);
		}
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}
}

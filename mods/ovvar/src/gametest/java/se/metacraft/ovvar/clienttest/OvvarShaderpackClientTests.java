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
 * the shader patcher (the ovvar-client jar) does not. Runs only with {@code -Dovvar.iris.expect=checker|art} (see the README,
 * "Client test under a shaderpack"); otherwise it does nothing and the vanilla client run is unchanged.
 *
 * <p>The verdict is {@link ShotMeasures#leg} over the right leg (pinned on real BSL frames by ShotMeasuresTest): the
 * checker is a two-colour pattern of violet-family texels (#FF00FF, or BSL's graded violet) and dark ones; the art is
 * Data cloth with no violet at all.
 */
public final class OvvarShaderpackClientTests implements FabricClientGameTest {
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
				// The mannequin faces the camera, so its right leg is on the screen's left of centre. The crop
				// must be mostly garment, or "art" would pass on a crop that missed the leg (or an ovve that never drew).
				ShotMeasures.Leg leg = ShotMeasures.leg(img, ShotMeasures.rightLeg(img.getWidth(), img.getHeight()),
						ShotMeasures.legBackgroundDx(img.getHeight()));
				System.out.println("[ovvar-clienttest] iris " + expect + ": right leg " + leg);
				if (!leg.onGarment()) {
					throw new AssertionError("the right-leg crop is not on the garment: " + leg);
				}
				if (expect.equals("checker") && !leg.showsChecker()) {
					throw new AssertionError("expected the unpatched pack's violet/dark checker on the right leg: " + leg);
				}
				if (expect.equals("art") && !leg.showsArt()) {
					throw new AssertionError("expected the Data cloth and no checker on the right leg of a patched pack: " + leg);
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

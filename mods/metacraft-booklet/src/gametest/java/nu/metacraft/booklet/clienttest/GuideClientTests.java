package nu.metacraft.booklet.clienttest;

import eu.pb4.booklet.impl.BookletImplUtil;
import eu.pb4.booklet.impl.BookletInit;
import eu.pb4.booklet.impl.BookletOpenState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.booklet.MetacraftBooklet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The guidebook as a vanilla client sees it: the index, then every page this mod ships, each
 * opened as the dialog a player gets, in a 1080p window, and photographed ({@code guide_*.png} in
 * the client test's screenshots directory, {@code build/run/clientGameTest/screenshots/}).
 *
 * <p>It asserts only that each page opens as a screen: {@code GuideTests} checks the pages on the
 * server, and the picture is the check for how they read.
 */
public final class GuideClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25602");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.getInput().resizeWindow(1920, 1080);
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");

				AtomicReference<List<Identifier>> ours = new AtomicReference<>();
				server.runOnServer(mc -> ours.set(BookletInit.PAGES.keySet().stream()
						.filter(id -> id.getNamespace().equals("metacraft"))
						.sorted(Comparator.comparing(Identifier::getPath)).toList()));
				List<Identifier> pages = new ArrayList<>();
				pages.add(MetacraftBooklet.INDEX);
				pages.addAll(ours.get());

				for (Identifier page : pages) {
					server.runOnServer(mc -> {
						ServerPlayer player = mc.getPlayerList().getPlayers().getFirst();
						if (!BookletImplUtil.openPage(player, page, BookletOpenState.DEFAULT)) {
							throw new AssertionError("Booklet could not open " + page);
						}
					});
					conn.waitForClientboundPackets();
					ctx.waitTicks(10);
					ctx.waitFor(client -> client.gui.screen() != null, 20 * 5);
					// The join toasts (unverified chat, social interactions) would sit over the text.
					ctx.runOnClient(client -> client.gui.toastManager().clear());
					ctx.waitTicks(2);
					String name = "guide_" + page.getNamespace() + "_" + page.getPath().replace('/', '_');
					ctx.takeScreenshot(TestScreenshotOptions.of(name));
					// And the rest of a long page: scrolled down over the dialog's body.
					ctx.getInput().setCursorPos(960, 540);
					ctx.getInput().scroll(-6);
					ctx.waitTicks(3);
					ctx.getInput().setCursorPos(0, 0);
					ctx.waitTicks(2);
					ctx.takeScreenshot(TestScreenshotOptions.of(name + "_more"));
					// and its foot
					ctx.getInput().setCursorPos(960, 540);
					ctx.getInput().scroll(-60);
					ctx.waitTicks(3);
					ctx.getInput().setCursorPos(0, 0);
					ctx.waitTicks(2);
					ctx.takeScreenshot(TestScreenshotOptions.of(name + "_end"));
					ctx.runOnClient(client -> client.gui.setScreen(null));
					ctx.waitTicks(5);
				}
			}
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}

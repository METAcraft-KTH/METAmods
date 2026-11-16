package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConfirmScreen;

import java.awt.image.BufferedImage;

/** Helpers the client tests share. */
final class ClientTestSupport {
	private ClientTestSupport() {}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}

	/**
	 * The unpatched-shaderpack checker is #FF00FF and black. Vanilla must never show it: in the box
	 * (clamped to the image) at most 0.2% of the pixels may be within 40 per channel of magenta.
	 */
	static void assertNoChecker(BufferedImage img, int x0, int y0, int x1, int y1, String what) {
		int ax = Math.max(0, x0), ay = Math.max(0, y0), bx = Math.min(img.getWidth(), x1), by = Math.min(img.getHeight(), y1);
		int n = 0;
		for (int y = ay; y < by; y++) {
			for (int x = ax; x < bx; x++) {
				int p = img.getRGB(x, y);
				if (255 - ((p >> 16) & 255) <= 40 && ((p >> 8) & 255) <= 40 && 255 - (p & 255) <= 40) n++;
			}
		}
		int budget = Math.max(1, (bx - ax) * (by - ay) / 500);
		if (n > budget) throw new AssertionError(what + ": " + n + " magenta pixel(s) in the subject box (budget " + budget + "), the pink-black checker is showing on vanilla");
	}

	/** Under -Piris the vanilla screenshot tests are skipped; OvvarShaderpackClientTests runs instead. */
	static boolean skipUnderShaderpack(String test) {
		String pack = System.getProperty("ovvar.iris.pack");
		if (pack == null || pack.isEmpty()) return false;
		System.out.println("[ovvar-clienttest] " + test + " skipped under shaderpack " + pack);
		return true;
	}
}

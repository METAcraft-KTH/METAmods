package se.metacraft.traders.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import se.metacraft.traders.TraderKind;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * A vanilla client (no Polymer handshake) joins an in-process server and visits each of the three traders: the model
 * must show (the view changes where it stands), a right-click must open the trade screen with that trader's offers
 * (written to the config here as the real offers are mod items the test run doesn't have), and the data trader's offer
 * must be buyable (32 iron for 3 emeralds) once per player: its {@code max_uses} is 1, so it is used up when reopened.
 */
public final class TraderClientTests implements FabricClientGameTest {

	private static final String TRADES = """
			{"trades": {
				"data": [{"price": {"item": "minecraft:iron_ingot", "count": 32}, "result": {"item": "minecraft:emerald", "count": 3}, "max_uses": 1}],
				"it": [
					{"price": {"item": "minecraft:iron_ingot", "count": 32}, "result": {"item": "minecraft:gold_ingot"}},
					{"price": {"item": "minecraft:iron_ingot", "count": 32}, "result": {"item": "minecraft:diamond"}}
				],
				"media": [{"price": {"item": "minecraft:iron_ingot", "count": 32}, "result": {"item": "minecraft:copper_ingot"}}]
			}}
			""";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", String.valueOf(freePort()));
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (var server = ctx.worldBuilder().createServer(props)) {
			try (var conn = server.connect()) {
				ctx.waitTicks(20);
				ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
				ctx.waitTicks(20);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("gamemode creative Tester");
				server.runCommand("give Tester minecraft:iron_ingot 40");

				try {
					Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("traders.json5"), TRADES);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				server.runCommand("reload");
				ctx.waitTicks(40);

				// The player 3 blocks north of where each trader stands, facing it. First the empty view, to compare against.
				server.runCommand("tp Tester 0.5 -60 -2.5 0 8");
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				var empty = read(ctx.takeScreenshot(TestScreenshotOptions.of("empty").withSize(1920, 1080)));

				// A creative player's hit doesn't move a trader: with no gravity it is never on the ground, so a knockback
				// would slide it about 4 blocks away.
				server.runCommand("summon traders:media_trader 0.5 -60 0.5 {Rotation:[180f,0f]}");
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				ctx.getInput().pressKey(options -> options.keyAttack);
				ctx.waitTicks(20);
				double hitZ = traderZ(ctx);
				System.out.println("[traders-clienttest] trader z after a creative hit=" + hitZ);
				check(Math.abs(hitZ - 0.5) < 0.01, "a creative hit moved the trader from z 0.5 to " + hitZ);
				server.runCommand("kill @e[type=traders:media_trader]");
				conn.waitForClientboundPackets();
				ctx.waitTicks(10);

				visit(ctx, server, conn, empty, TraderKind.DATA, 1);
				visit(ctx, server, conn, empty, TraderKind.IT, 2);
				visit(ctx, server, conn, empty, TraderKind.MEDIA, 1);

				// Buy the data trader's offer: select it, take the result with a shift-click (once: 40 iron pays for one 32-iron offer).
				server.runCommand("summon traders:data_trader 0.5 -60 0.5 {Rotation:[180f,0f]}");
				conn.waitForClientboundPackets();
				ctx.waitTicks(20);
				open(ctx);
				ctx.runOnClient(client -> client.getConnection().send(new ServerboundSelectTradePacket(0)));
				ctx.waitTicks(10);
				ctx.runOnClient(client -> {
					var menu = client.player.containerMenu;
					client.gameMode.handleContainerInput(menu.containerId, 2, 0, ContainerInput.QUICK_MOVE, client.player);
				});
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("data_trader_after_trade").withSize(1920, 1080));
				int emeralds = ctx.computeOnClient(client -> client.player.getInventory().countItem(Items.EMERALD));
				// Selecting an offer moves the payment into the screen's input slots, so count the whole menu.
				int iron = ctx.computeOnClient(client -> client.player.containerMenu.slots.stream()
						.filter(slot -> slot.getItem().is(Items.IRON_INGOT)).mapToInt(slot -> slot.getItem().getCount()).sum());
				System.out.println("[traders-clienttest] emeralds=" + emeralds + " iron=" + iron);
				check(emeralds == 3, "the trade didn't give the result: " + emeralds + " emeralds");
				check(iron == 8, "the trade didn't take the price: " + iron + " iron left of 40");

				// The data trade is max_uses 1 per player: opened again, with iron enough for another, it is used up and sells nothing.
				ctx.runOnClient(client -> client.player.closeContainer());
				server.runCommand("give Tester minecraft:iron_ingot 64");
				conn.waitForClientboundPackets();
				ctx.waitTicks(10);
				open(ctx);
				boolean usedUp = ctx.computeOnClient(client -> client.player.containerMenu instanceof MerchantMenu menu
						&& menu.getOffers().getFirst().isOutOfStock());
				ctx.runOnClient(client -> client.getConnection().send(new ServerboundSelectTradePacket(0)));
				ctx.waitTicks(10);
				ctx.runOnClient(client -> {
					var menu = client.player.containerMenu;
					client.gameMode.handleContainerInput(menu.containerId, 2, 0, ContainerInput.QUICK_MOVE, client.player);
				});
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("data_trader_used_up").withSize(1920, 1080));
				int emeraldsAgain = ctx.computeOnClient(client -> client.player.getInventory().countItem(Items.EMERALD));
				System.out.println("[traders-clienttest] reopened: used up=" + usedUp + " emeralds=" + emeraldsAgain);
				check(usedUp, "the one-use data trade isn't used up when its screen opens again");
				check(emeraldsAgain == 3, "the used-up data trade sold again: " + emeraldsAgain + " emeralds");

				// /traders spawn puts all three in a row in front of the player.
				ctx.runOnClient(client -> client.player.closeContainer());
				server.runCommand("kill @e[type=traders:data_trader]");
				server.runCommand("tp Tester 0.5 -60 -2.5 0 8");
				server.runCommand("execute as Tester at Tester run traders spawn");
				conn.waitForClientboundPackets();
				ctx.waitTicks(30);
				var row = read(ctx.takeScreenshot(TestScreenshotOptions.of("traders_spawn").withSize(1920, 1080)));
				for (int dx : new int[] {-485, 0, 485}) {
					int x = row.getWidth() / 2 + dx - 80, y = 520, w = 160, h = 280;
					int changed = changed(empty.getSubimage(x, y, w, h), row.getSubimage(x, y, w, h));
					System.out.println("[traders-clienttest] spawned row at " + dx + " changed pixels=" + changed);
					check(changed >= 2000, "/traders spawn: no trader at " + dx + " px from the middle (" + changed + " pixels differ)");
				}

				// Spawned in mid-air they stay there: no gravity. (The flat world's ground is at y -61; the traders are put at y -55.)
				server.runCommand("kill @e[type=traders:data_trader]");
				server.runCommand("kill @e[type=traders:it_trader]");
				server.runCommand("kill @e[type=traders:media_trader]");
				server.runCommand("tp Tester 0.5 -55 -2.5 0 8");
				server.runCommand("execute as Tester at Tester run traders spawn");
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				double lowest = ctx.computeOnClient(client -> client.level.getEntitiesOfClass(Interaction.class, client.player.getBoundingBox().inflate(8))
						.stream().mapToDouble(Entity::getY).min().orElse(Double.NaN));
				System.out.println("[traders-clienttest] lowest trader y after 40 ticks in mid-air=" + lowest);
				check(lowest > -55.5, "a trader fell: its y is " + lowest + ", it was put at -55");
			}
		}
	}

	/** Summons the trader, checks its model shows and its trade screen has {@code offers} offers, then removes it. */
	private static void visit(ClientGameTestContext ctx, TestDedicatedServerContext server, TestServerConnection conn, BufferedImage empty, TraderKind kind, int offers) {
		server.runCommand("summon traders:" + kind.entityName() + " 0.5 -60 0.5 {Rotation:[180f,0f]}");
		conn.waitForClientboundPackets();
		ctx.waitTicks(20);
		var shot = read(ctx.takeScreenshot(TestScreenshotOptions.of(kind.entityName() + "_front").withSize(1920, 1080)));
		// the trader stands in the middle of the view, from the chest to the knees (the toasts and chat are off to the left)
		int x = shot.getWidth() / 2 - 130, y = shot.getHeight() * 4 / 10, w = 260, h = shot.getHeight() * 3 / 10;
		int changed = changed(empty.getSubimage(x, y, w, h), shot.getSubimage(x, y, w, h));
		System.out.println("[traders-clienttest] " + kind.entityName() + " changed pixels=" + changed);
		check(changed >= 4000, "the " + kind.entityName() + "'s model doesn't show: only " + changed + " pixels differ from the empty view");
		// a texture that failed to load or whose UVs miss it is drawn as the magenta and black checkerboard
		int missing = count(shot.getSubimage(x, y, w, h), TraderClientTests::missingTexturePixel);
		System.out.println("[traders-clienttest] " + kind.entityName() + " missing-texture pixels=" + missing);
		check(missing < 200, "the " + kind.entityName() + "'s texture is missing: " + missing + " magenta pixels");

		open(ctx);
		ctx.takeScreenshot(TestScreenshotOptions.of(kind.entityName() + "_trade_screen").withSize(1920, 1080));
		int shown = ctx.computeOnClient(client -> client.player.containerMenu instanceof MerchantMenu menu ? menu.getOffers().size() : -1);
		System.out.println("[traders-clienttest] " + kind.entityName() + " offers=" + shown);
		check(shown == offers, "the " + kind.entityName() + " shows " + shown + " offers, not " + offers);

		ctx.runOnClient(client -> client.player.closeContainer());
		ctx.waitTicks(5);
		server.runCommand("kill @e[type=traders:" + kind.entityName() + "]");
		conn.waitForClientboundPackets();
		ctx.waitTicks(10);
	}

	/**
	 * Right-clicks what the player looks at the way a player does, through the use key: the trade screen opens. (A raw
	 * {@code pressMouse(1)} reaches this test client as an attack, so it isn't used.)
	 */
	private static void open(ClientGameTestContext ctx) {
		String target = ctx.computeOnClient(client -> String.valueOf(client.hitResult) + " / " + client.crosshairPickEntity);
		System.out.println("[traders-clienttest] crosshair=" + target);
		ctx.getInput().pressKey(options -> options.keyUse);
		ctx.waitFor(client -> client.gui.screen() instanceof MerchantScreen, 100);
		ctx.waitTicks(10);
	}

	/** Where the one trader near the player stands, by its interaction box. */
	private static double traderZ(ClientGameTestContext ctx) {
		return ctx.computeOnClient(client -> client.level.getEntitiesOfClass(Interaction.class, client.player.getBoundingBox().inflate(8))
				.stream().mapToDouble(Entity::getZ).findFirst().orElse(Double.NaN));
	}

	/** How many pixels of the two same-sized images differ noticeably. */
	private static int changed(BufferedImage a, BufferedImage b) {
		int n = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				int p = a.getRGB(x, y), q = b.getRGB(x, y);
				int diff = Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF)) + Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF)) + Math.abs((p & 0xFF) - (q & 0xFF));
				if (diff > 40) {
					n++;
				}
			}
		}
		return n;
	}

	/** The missing-texture magenta, however lit. */
	private static boolean missingTexturePixel(int rgb) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		return r > 120 && b > 120 && g < 40;
	}

	private static int count(BufferedImage image, java.util.function.IntPredicate test) {
		int n = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (test.test(image.getRGB(x, y))) {
					n++;
				}
			}
		}
		return n;
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	private static int freePort() {
		try (var socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}

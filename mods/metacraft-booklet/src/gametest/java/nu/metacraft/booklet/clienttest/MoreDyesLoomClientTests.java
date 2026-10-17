package nu.metacraft.booklet.clienttest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * More Dyes' dyes in the vanilla loom, the sixth pattern on a banner of our colour there, and banners of
 * our colours with six patterns on top (seven layers), as a vanilla client sees them
 * ({@code moredyes_loom_*.png}, {@code moredyes_seven_*.png}).
 *
 * <p>The loom is driven as a player would: shift-click a white banner and Cerise dye in, pick a pattern,
 * take the result with the mouse. Picking the pattern is done in its two halves (the client's own
 * guess, then the message to the server) so the client's guess at the result (the pattern in the dye's
 * nearest vanilla colour), which a player sees until the server's result arrives, can be photographed.
 * It asserts that the client and the server end up agreeing on every loom slot and on the taken banner.
 * Then, for the side effects of the client believing our dye is a vanilla one: a tamed wolf's collar
 * and a sheep dyed by the client.
 *
 * <pre>
 *   METACRAFT_MOREDYES_LOOM=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
 * </pre>
 */
public final class MoreDyesLoomClientTests implements FabricClientGameTest {
	private static final BlockPos LOOM = new BlockPos(2, -60, -2);
	private static final String PATTERN = "cross";
	private static final Identifier CERISE_PATTERN = Identifier.fromNamespaceAndPath("moredyes", "cerise/minecraft/" + PATTERN);
	private static final Identifier CERISE_BASE = Identifier.fromNamespaceAndPath("moredyes", "cerise/minecraft/base");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"1".equals(System.getenv("METACRAFT_MOREDYES_LOOM"))) return;
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25605");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ctx.getInput().resizeWindow(1920, 1080);
				ctx.waitTicks(20);
				ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
				ctx.waitTicks(20);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("weather clear");
				server.runCommand("gamemode survival Tester");
				server.runCommand("tp Tester 0.5 -60 0.5 180 8");
				server.runCommand("clear Tester");
				ctx.waitTicks(10);

				loom(ctx, server, conn);
				sixthOnColour(ctx, server, conn);
				sevenLayers(ctx, server, conn);
				sideEffects(ctx, server, conn);
			}
		}
	}

	private static Item dye(String color) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("moredyes", color + "_dye"));
	}

	private static ServerPlayer tester(MinecraftServer mc) {
		return mc.getPlayerList().getPlayerByName("Tester");
	}

	/** Window coordinates of a point in a 176×166 container screen's own coordinates. */
	private static double[] at(ClientGameTestContext ctx, int x, int y) {
		return ctx.computeOnClient(client -> {
			var window = client.getWindow();
			int scale = window.getGuiScale();
			int left = (window.getGuiScaledWidth() - 176) / 2, top = (window.getGuiScaledHeight() - 166) / 2;
			return new double[]{(left + x + 0.5) * scale, (top + y + 0.5) * scale};
		});
	}

	private static void click(ClientGameTestContext ctx, int x, int y) {
		double[] p = at(ctx, x, y);
		ctx.getInput().setCursorPos(p[0], p[1]);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static String describe(ItemStack stack) {
		if (stack.isEmpty()) return "empty";
		List<String> layers = new ArrayList<>();
		for (var layer : stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers()) {
			layers.add(layer.pattern().unwrapKey().map(k -> k.identifier().toString()).orElse("?") + "/" + layer.color().getName());
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount() + layers;
	}

	private static boolean topIs(ItemStack stack, Identifier pattern, DyeColor color) {
		var layers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
		return !layers.isEmpty() && layers.getLast().pattern().is(ResourceKey.create(Registries.BANNER_PATTERN, pattern))
				&& layers.getLast().color() == color;
	}

	private static void loom(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			ServerPlayer tester = tester(mc);
			level.setBlockAndUpdate(LOOM, Blocks.LOOM.defaultBlockState());
			tester.getInventory().setItem(0, new ItemStack(Items.BANNER.white(), 2));
			tester.getInventory().setItem(1, new ItemStack(dye("cerise"), 2));
			tester.openMenu(new SimpleMenuProvider((id, inventory, player) -> new LoomMenu(id, inventory, ContainerLevelAccess.create(level, LOOM)),
					Component.translatable("container.loom")));
		});
		conn.waitForClientboundPackets();
		ctx.waitForScreen(LoomScreen.class);
		ctx.runOnClient(client -> client.gui.toastManager().clear());
		ctx.waitTicks(5);

		// Shift-click the banner and the dye in from the hotbar. The test input's shift does not reach a
		// 26.3 screen's click, so this is the call the screen makes for a shift-click.
		for (int hotbar = 0; hotbar < 2; hotbar++) {
			int inventorySlot = hotbar;
			ctx.runOnClient(client -> {
				LoomMenu menu = (LoomMenu) client.player.containerMenu;
				int slot = -1;
				for (var s : menu.slots) {
					if (s.container == client.player.getInventory() && s.getContainerSlot() == inventorySlot) slot = s.index;
				}
				client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, client.player);
			});
			ctx.waitTicks(5);
		}
		ctx.waitTicks(5);
		String clientInputs = ctx.computeOnClient(client -> {
			LoomMenu menu = (LoomMenu) client.player.containerMenu;
			return describe(menu.getBannerSlot().getItem()) + " | " + describe(menu.getDyeSlot().getItem())
					+ " | client dye component " + menu.getDyeSlot().getItem().get(DataComponents.DYE)
					+ " | patterns listed " + menu.getSelectablePatterns().size();
		});
		String serverInputs = server.computeOnServer(mc -> {
			LoomMenu menu = (LoomMenu) tester(mc).containerMenu;
			return describe(menu.getBannerSlot().getItem()) + " | " + describe(menu.getDyeSlot().getItem());
		});
		System.out.println("[moredyes-loom] after shift-clicks: client " + clientInputs + " ; server " + serverInputs);
		if (!serverInputs.contains("moredyes:cerise_dyex2")) throw new AssertionError("the server's dye slot does not hold the Cerise dye: " + serverInputs);
		if (!clientInputs.contains("minecraft:white_bannerx2")) throw new AssertionError("the client's banner slot: " + clientInputs);

		int index = ctx.computeOnClient(client -> {
			List<Holder<BannerPattern>> patterns = ((LoomMenu) client.player.containerMenu).getSelectablePatterns();
			for (int i = 0; i < patterns.size(); i++) {
				if (patterns.get(i).is(ResourceKey.create(Registries.BANNER_PATTERN, Identifier.withDefaultNamespace(PATTERN)))) return i;
			}
			return -1;
		});
		if (index < 0 || index >= 16) throw new AssertionError("the loom lists " + PATTERN + " at " + index);

		// Clicking a pattern, LoomScreen first runs the menu on the client's view (clickMenuButton) and then
		// tells the server. Doing the two halves apart shows what the player sees until the server's
		// result arrives (one round trip plus up to a server tick): the client's own guess.
		ctx.runOnClient(client -> ((LoomMenu) client.player.containerMenu).clickMenuButton(client.player, index));
		String guessed = ctx.computeOnClient(client -> describe(((LoomMenu) client.player.containerMenu).getResultSlot().getItem()));
		double[] away = at(ctx, -40, 20);
		ctx.getInput().setCursorPos(away[0], away[1]);
		ctx.waitTicks(2);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_client_guess"));
		ctx.runOnClient(client -> client.gameMode.handleInventoryButtonClick(client.player.containerMenu.containerId, index));
		ctx.waitFor(client -> topIs(((LoomMenu) client.player.containerMenu).getResultSlot().getItem(), CERISE_PATTERN, DyeColor.WHITE), 20 * 15);
		String settled = ctx.computeOnClient(client -> describe(((LoomMenu) client.player.containerMenu).getResultSlot().getItem()));
		System.out.println("[moredyes-loom] result on the client before the server answered: " + guessed + " ; once it answered: " + settled);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_selected"));
		int other = index + 1;  // a button that is not the selected one shows its name
		double[] button = at(ctx, 60 + (other % 4) * 14 + 7, 13 + (other / 4) * 14 + 7);
		ctx.getInput().setCursorPos(button[0], button[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_button_tooltip"));
		double[] result = at(ctx, 143 + 8, 57 + 8);
		ctx.getInput().setCursorPos(result[0], result[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_result_tooltip"));

		// take it, and put it down in the first hotbar slot
		click(ctx, 143 + 8, 57 + 8);
		String rightAfterTake = ctx.computeOnClient(client -> "carried " + describe(client.player.containerMenu.getCarried())
				+ " result " + describe(((LoomMenu) client.player.containerMenu).getResultSlot().getItem()));
		ctx.waitTicks(10);
		click(ctx, 8 + 8, 142 + 8);
		ctx.waitTicks(20);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		String client = ctx.computeOnClient(c -> slots((LoomMenu) c.player.containerMenu) + " carried " + describe(c.player.containerMenu.getCarried()));
		String srv = server.computeOnServer(mc -> slots((LoomMenu) tester(mc).containerMenu) + " carried " + describe(tester(mc).containerMenu.getCarried()));
		System.out.println("[moredyes-loom] right after the take (client): " + rightAfterTake);
		System.out.println("[moredyes-loom] after the take: client " + client + " ; server " + srv);
		if (!client.equals(srv.replace("moredyes:cerise_dye", "minecraft:white_dye"))) {
			throw new AssertionError("client and server disagree after the take:\n client " + client + "\n server " + srv);
		}
		ItemStack taken = ctx.computeOnClient(c -> c.player.getInventory().getItem(0).copy());
		if (!topIs(taken, CERISE_PATTERN, DyeColor.WHITE)) throw new AssertionError("the client holds " + describe(taken));
		ItemStack takenOnServer = server.computeOnServer(mc -> tester(mc).getInventory().getItem(0).copy());
		if (!topIs(takenOnServer, CERISE_PATTERN, DyeColor.WHITE) || takenOnServer.getCount() != 1) {
			throw new AssertionError("the server gave " + describe(takenOnServer));
		}
		ctx.getInput().setCursorPos(at(ctx, 8 + 8, 142 + 8)[0], at(ctx, 8 + 8, 142 + 8)[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_taken"));

		server.runOnServer(mc -> tester(mc).closeContainer());
		ctx.waitFor(c -> c.gui.screen() == null, 20 * 10);
		server.runCommand("clear Tester");
		ctx.waitTicks(5);
	}

	/**
	 * A Cerise banner with five patterns on top, shift-clicked into the loom with Cerise dye: the client is
	 * shown the five on a plain banner of the nearest vanilla colour (its loom screen would see six layers
	 * and list nothing), so the pattern buttons are there to click with the mouse; the result and the
	 * banner taken are the real Cerise banner with six patterns on top ({@code moredyes_loom_sixth_*.png}).
	 */
	private static void sixthOnColour(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			ServerPlayer tester = tester(mc);
			level.setBlockAndUpdate(LOOM, Blocks.LOOM.defaultBlockState());
			ItemStack five = banner(mc, false);
			tester.getInventory().setItem(0, five);
			tester.getInventory().setItem(1, new ItemStack(dye("cerise"), 2));
			tester.openMenu(new SimpleMenuProvider((id, inventory, player) -> new LoomMenu(id, inventory, ContainerLevelAccess.create(level, LOOM)),
					Component.translatable("container.loom")));
		});
		conn.waitForClientboundPackets();
		ctx.waitForScreen(LoomScreen.class);
		ctx.runOnClient(client -> client.gui.toastManager().clear());
		ctx.waitTicks(5);
		for (int hotbar = 0; hotbar < 2; hotbar++) {
			int inventorySlot = hotbar;
			ctx.runOnClient(client -> {
				LoomMenu menu = (LoomMenu) client.player.containerMenu;
				int slot = -1;
				for (var s : menu.slots) {
					if (s.container == client.player.getInventory() && s.getContainerSlot() == inventorySlot) slot = s.index;
				}
				client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, client.player);
			});
			ctx.waitTicks(5);
		}
		ctx.waitTicks(5);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		String clientBanner = ctx.computeOnClient(client -> describe(((LoomMenu) client.player.containerMenu).getBannerSlot().getItem()));
		String serverBanner = server.computeOnServer(mc -> describe(((LoomMenu) tester(mc).containerMenu).getBannerSlot().getItem()));
		System.out.println("[moredyes-loom] sixth on colour, banner slot: client " + clientBanner + " ; server " + serverBanner);
		if (clientBanner.contains(CERISE_BASE.toString())) throw new AssertionError("the client sees the colour layer: " + clientBanner);
		if (!serverBanner.contains(CERISE_BASE.toString())) throw new AssertionError("the server lost the colour layer: " + serverBanner);
		double[] away = at(ctx, -40, 20);
		ctx.getInput().setCursorPos(away[0], away[1]);
		ctx.waitTicks(2);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_sixth_listed"));

		int index = ctx.computeOnClient(client -> {
			List<Holder<BannerPattern>> patterns = ((LoomMenu) client.player.containerMenu).getSelectablePatterns();
			for (int i = 0; i < patterns.size(); i++) {
				if (patterns.get(i).is(ResourceKey.create(Registries.BANNER_PATTERN, Identifier.withDefaultNamespace(PATTERN)))) return i;
			}
			return -1;
		});
		if (index < 0 || index >= 16) throw new AssertionError("the loom lists " + PATTERN + " at " + index);
		// the button, clicked with the mouse: the screen only takes the click when it lists the patterns
		click(ctx, 60 + (index % 4) * 14 + 7, 13 + (index / 4) * 14 + 7);
		ctx.waitFor(client -> topIs(((LoomMenu) client.player.containerMenu).getResultSlot().getItem(), CERISE_PATTERN, DyeColor.WHITE), 20 * 15);
		String result = ctx.computeOnClient(client -> describe(((LoomMenu) client.player.containerMenu).getResultSlot().getItem()));
		System.out.println("[moredyes-loom] sixth on colour, result on the client: " + result);
		if (!result.contains(CERISE_BASE.toString())) throw new AssertionError("the result has no colour layer: " + result);
		ctx.getInput().setCursorPos(away[0], away[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_sixth_selected"));

		click(ctx, 143 + 8, 57 + 8);
		ctx.waitTicks(10);
		click(ctx, 8 + 8, 142 + 8);
		ctx.waitTicks(20);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		ItemStack taken = ctx.computeOnClient(c -> c.player.getInventory().getItem(0).copy());
		ItemStack takenOnServer = server.computeOnServer(mc -> tester(mc).getInventory().getItem(0).copy());
		System.out.println("[moredyes-loom] sixth on colour, taken: client " + describe(taken) + " ; server " + describe(takenOnServer));
		for (ItemStack stack : List.of(taken, takenOnServer)) {
			var layers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
			if (layers.size() != 7 || !layers.getFirst().pattern().is(ResourceKey.create(Registries.BANNER_PATTERN, CERISE_BASE))
					|| !topIs(stack, CERISE_PATTERN, DyeColor.WHITE)) {
				throw new AssertionError("taken: " + describe(stack));
			}
		}
		ctx.getInput().setCursorPos(at(ctx, 8 + 8, 142 + 8)[0], at(ctx, 8 + 8, 142 + 8)[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_loom_sixth_taken"));

		server.runOnServer(mc -> tester(mc).closeContainer());
		ctx.waitFor(c -> c.gui.screen() == null, 20 * 10);
		server.runCommand("clear Tester");
		ctx.waitTicks(5);
	}

	private static String slots(LoomMenu menu) {
		return "banner " + describe(menu.getBannerSlot().getItem()) + " dye " + describe(menu.getDyeSlot().getItem())
				+ " result " + describe(menu.getResultSlot().getItem());
	}

	/**
	 * The client believes our dye is a vanilla dye of the nearest colour. On a tamed wolf it predicts
	 * that collar, which the server sets too; on a sheep it plays the dye sound at most, and the sheep
	 * takes our colour from the server.
	 */
	private static void sideEffects(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		AtomicReference<Integer> ids = new AtomicReference<>();
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			ServerPlayer tester = tester(mc);
			Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
			wolf.snapTo(0.5, -60, -2.5, 0, 0);
			wolf.tame(tester);
			wolf.setOrderedToSit(true);
			level.addFreshEntity(wolf);
			Sheep sheep = EntityTypes.SHEEP.create(level, EntitySpawnReason.COMMAND);
			sheep.snapTo(-1.5, -60, -2.5, 0, 0);
			sheep.setNoAi(true);
			level.addFreshEntity(sheep);
			ids.set(wolf.getId() * 100000 + sheep.getId());
			tester.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dye("cerise"), 4));
		});
		conn.waitForClientboundPackets();
		ctx.waitTicks(20);
		int wolfId = ids.get() / 100000, sheepId = ids.get() % 100000;
		String predicted = ctx.computeOnClient(client -> {
			var wolf = client.level.getEntity(wolfId);
			var sheep = client.level.getEntity(sheepId);
			var onWolf = client.gameMode.interact(client.player, wolf, new EntityHitResult(wolf), InteractionHand.MAIN_HAND);
			String wolfNow = ((Wolf) wolf).getCollarColor().getName();
			var onSheep = client.gameMode.interact(client.player, sheep, new EntityHitResult(sheep), InteractionHand.MAIN_HAND);
			return "wolf " + onWolf + " collar " + wolfNow + "; sheep " + onSheep;
		});
		ctx.waitTicks(20);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		String clientSide = ctx.computeOnClient(client -> "wolf collar " + ((Wolf) client.level.getEntity(wolfId)).getCollarColor().getName()
				+ ", sheep colour " + ((Sheep) client.level.getEntity(sheepId)).getColor().getName()
				+ ", dyes " + client.player.getMainHandItem().getCount());
		String serverSide = server.computeOnServer(mc -> {
			var level = mc.overworld();
			return "wolf collar " + ((Wolf) level.getEntity(wolfId)).getCollarColor().getName()
					+ ", sheep colour " + ((Sheep) level.getEntity(sheepId)).getColor().getName()
					+ ", dyes " + tester(mc).getMainHandItem().getCount();
		});
		System.out.println("[moredyes-loom] side effects: client predicted " + predicted + " ; client now " + clientSide + " ; server " + serverSide);
		if (!clientSide.equals(serverSide)) throw new AssertionError("client and server disagree: client " + clientSide + ", server " + serverSide);
		server.runCommand("kill @e[type=!player]");
		server.runCommand("clear Tester");
		ctx.waitTicks(10);
	}

	private static ItemStack banner(MinecraftServer mc, boolean seventh) {
		var registry = mc.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<BannerPatternLayers.Layer> layers = new ArrayList<>();
		layers.add(new BannerPatternLayers.Layer(registry.getOrThrow(ResourceKey.create(Registries.BANNER_PATTERN, CERISE_BASE)), DyeColor.WHITE));
		layers.add(new BannerPatternLayers.Layer(registry.getOrThrow(ResourceKey.create(Registries.BANNER_PATTERN,
				Identifier.fromNamespaceAndPath("moredyes", "laserviolet/minecraft/border"))), DyeColor.WHITE));
		String[] vanilla = {"stripe_top", "stripe_bottom", "stripe_left", "stripe_right"};
		DyeColor[] colours = {DyeColor.YELLOW, DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.LIGHT_BLUE};
		for (int i = 0; i < vanilla.length; i++) {
			layers.add(new BannerPatternLayers.Layer(registry.getOrThrow(ResourceKey.create(Registries.BANNER_PATTERN,
					Identifier.withDefaultNamespace(vanilla[i]))), colours[i]));
		}
		if (seventh) {
			layers.add(new BannerPatternLayers.Layer(registry.getOrThrow(ResourceKey.create(Registries.BANNER_PATTERN,
					Identifier.withDefaultNamespace("creeper"))), DyeColor.BLACK));
		}
		ItemStack stack = new ItemStack(Items.BANNER.white());
		stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("block.moredyes.cerise_banner", "Cerise Banner"));
		return stack;
	}

	/**
	 * A Cerise banner with six patterns on top (seven layers; the topmost a black creeper) next to the
	 * same banner without the creeper, placed, in hand and on a shield.
	 */
	private static void sevenLayers(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn) {
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			ServerPlayer tester = tester(mc);
			level.setBlockAndUpdate(LOOM, Blocks.AIR.defaultBlockState());
			List<ItemStack> row = List.of(banner(mc, false), banner(mc, true));
			for (int i = 0; i < row.size(); i++) {
				BlockPos floor = new BlockPos(i * 2 - 1, -61, -3);
				tester.setItemInHand(InteractionHand.MAIN_HAND, row.get(i).copy());
				var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
				tester.getMainHandItem().useOn(new UseOnContext(tester, InteractionHand.MAIN_HAND, hit));
				if (!(level.getBlockEntity(floor.above()) instanceof BannerBlockEntity placed)
						|| placed.getPatterns().layers().size() != row.get(i).get(DataComponents.BANNER_PATTERNS).layers().size()) {
					throw new AssertionError("the banner at " + floor.above() + " did not keep its layers");
				}
			}
			ItemStack shield = new ItemStack(Items.SHIELD);
			shield.set(DataComponents.BASE_COLOR, DyeColor.WHITE);
			shield.set(DataComponents.BANNER_PATTERNS, banner(mc, true).get(DataComponents.BANNER_PATTERNS));
			tester.setItemInHand(InteractionHand.OFF_HAND, shield.copy());
			tester.setItemInHand(InteractionHand.MAIN_HAND, banner(mc, true));
			// the shield's face, which first person shows only from behind, in an item frame
			BlockPos wall = new BlockPos(-3, -59, -4);
			level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
			ItemFrame frame = new ItemFrame(level, wall.south(), Direction.SOUTH);
			frame.setItem(shield.copy(), false);
			level.addFreshEntity(frame);
		});
		conn.waitForClientboundPackets();
		ctx.waitTicks(40);
		ctx.runOnClient(client -> {
			if (!(client.level.getBlockEntity(new BlockPos(1, -60, -3)) instanceof BannerBlockEntity placed) || placed.getPatterns().layers().size() != 7) {
				throw new AssertionError("the client's placed banner does not have seven layers");
			}
			int inHand = client.player.getMainHandItem().getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
			int onShield = client.player.getOffhandItem().getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
			if (inHand != 7 || onShield != 7) throw new AssertionError("in hand " + inHand + " layers, on the shield " + onShield);
			client.gui.toastManager().clear();
			client.gui.hud.getChat().clearMessages(false);
		});
		ctx.getInput().setCursorPos(0, 0);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_seven_placed_and_in_hand"));

		ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		ctx.waitForScreen(InventoryScreen.class);
		double[] slot = at(ctx, 8 + 8, 142 + 8);
		ctx.getInput().setCursorPos(slot[0], slot[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_seven_tooltip"));
		ctx.setScreen(() -> null);
		ctx.waitTicks(5);
	}
}

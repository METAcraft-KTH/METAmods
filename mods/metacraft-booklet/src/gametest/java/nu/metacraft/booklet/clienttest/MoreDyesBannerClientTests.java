package nu.metacraft.booklet.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * More Dyes' coloured banners as a vanilla client sees them: six Cerise wool and a stick in a
 * crafting table (the result hovered, so its name and layer show), then Cerise and Laserviolet
 * banners crafted the same way, placed next to a plain white one, with a Cerise banner in hand and a
 * shield decorated with one ({@code moredyes_banner_*.png} in the client test's screenshots).
 *
 * <p>It asserts that what the client got is the banner: the crafting result and the placed banners
 * carry the derived full-cover layer, which the client's synced pattern registry knows. Whether it
 * shows in the colour is the picture's to say.
 *
 * <pre>
 *   METACRAFT_MOREDYES_BANNERS=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
 * </pre>
 */
public final class MoreDyesBannerClientTests implements FabricClientGameTest {
	private static final Identifier CERISE_BASE = Identifier.fromNamespaceAndPath("moredyes", "cerise/minecraft/base");
	private static final BlockPos TABLE = new BlockPos(3, -60, 3);

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"1".equals(System.getenv("METACRAFT_MOREDYES_BANNERS"))) return;
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25604");
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

				// The recipe, in a real crafting menu: the server's recipe manager fills the result.
				ItemStack cerise = craft(ctx, server, conn, "cerise");
				int[] cursor = ctx.computeOnClient(client -> {
					assertBase(client.player.containerMenu.getSlot(0).getItem(), "the crafting result the client sees");
					var window = client.getWindow();
					int scale = window.getGuiScale();
					int left = (window.getGuiScaledWidth() - 176) / 2, top = (window.getGuiScaledHeight() - 166) / 2;
					// over the result slot (124, 35), so its tooltip shows
					return new int[]{(left + 132) * scale, (top + 43) * scale};
				});
				ctx.getInput().setCursorPos(cursor[0], cursor[1]);
				// The join toasts (unverified chat, social interactions, new recipes) would sit over the result.
				ctx.runOnClient(client -> client.gui.toastManager().clear());
				ctx.waitTicks(10);
				ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_banner_recipe"));
				closeCrafting(ctx, server);
				ItemStack laserviolet = craft(ctx, server, conn, "laserviolet");
				closeCrafting(ctx, server);
				server.runCommand("clear Tester");

				server.runOnServer(mc -> {
					ServerLevel level = mc.overworld();
					ServerPlayer tester = mc.getPlayerList().getPlayerByName("Tester");
					// a Cerise, a plain white and a Laserviolet banner, placed the way a player does
					List<ItemStack> row = List.of(cerise, new ItemStack(Items.BANNER.white()), laserviolet);
					for (int i = 0; i < row.size(); i++) {
						BlockPos floor = new BlockPos(i - 1, -61, -3);
						tester.setItemInHand(InteractionHand.MAIN_HAND, row.get(i).copy());
						var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
						tester.getMainHandItem().useOn(new UseOnContext(tester, InteractionHand.MAIN_HAND, hit));
						if (!(level.getBlockEntity(floor.above()) instanceof BannerBlockEntity)) {
							throw new AssertionError("no banner placed at " + floor.above());
						}
					}
					ItemStack shield = new ItemStack(Items.SHIELD);
					shield.set(DataComponents.BASE_COLOR, net.minecraft.world.item.DyeColor.WHITE);
					shield.set(DataComponents.BANNER_PATTERNS, cerise.get(DataComponents.BANNER_PATTERNS));
					tester.setItemInHand(InteractionHand.OFF_HAND, shield);
					tester.setItemInHand(InteractionHand.MAIN_HAND, cerise.copy());
				});
				conn.waitForClientboundPackets();
				ctx.waitTicks(40);
				ctx.runOnClient(client -> {
					if (!(client.level.getBlockEntity(new BlockPos(-1, -60, -3)) instanceof BannerBlockEntity placed)) {
						throw new AssertionError("the client has no banner where the Cerise one was placed");
					}
					ItemStack asItem = new ItemStack(Items.BANNER.white());
					asItem.set(DataComponents.BANNER_PATTERNS, placed.getPatterns());
					assertBase(asItem, "the placed banner the client sees");
					assertBase(client.player.getMainHandItem(), "the banner in the client's hand");
				});
				ctx.getInput().setCursorPos(0, 0);
				ctx.runOnClient(client -> client.gui.toastManager().clear());
				ctx.waitTicks(5);
				ctx.takeScreenshot(TestScreenshotOptions.of("moredyes_banner_placed"));
			}
		}
	}

	/** Empties the grid (so nothing falls into the inventory) and closes the table. */
	private static void closeCrafting(ClientGameTestContext ctx, TestDedicatedServerContext server) {
		server.runOnServer(mc -> {
			ServerPlayer tester = mc.getPlayerList().getPlayerByName("Tester");
			if (tester.containerMenu instanceof CraftingMenu menu) {
				for (var slot : menu.getInputGridSlots()) slot.set(ItemStack.EMPTY);
			}
			tester.closeContainer();
		});
		ctx.waitFor(client -> client.gui.screen() == null, 20 * 10);
	}

	/** Six wool of the colour and a stick in a crafting table's grid; returns the result the server computed. */
	private static ItemStack craft(ClientGameTestContext ctx, TestDedicatedServerContext server, TestDedicatedServerConnection conn, String color) {
		AtomicReference<ItemStack> result = new AtomicReference<>();
		server.runOnServer(mc -> {
			ServerLevel level = mc.overworld();
			ServerPlayer tester = mc.getPlayerList().getPlayerByName("Tester");
			level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
			tester.openMenu(new SimpleMenuProvider((id, inventory, player) -> new CraftingMenu(id, inventory, ContainerLevelAccess.create(level, TABLE)),
					Component.translatable("container.crafting")));
			CraftingMenu menu = (CraftingMenu) tester.containerMenu;
			var wool = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("moredyes", color + "_wool"));
			for (int i = 0; i < 6; i++) menu.getInputGridSlots().get(i).set(new ItemStack(wool));
			menu.getInputGridSlots().get(7).set(new ItemStack(Items.STICK));
			menu.broadcastChanges();
			result.set(menu.getResultSlot().getItem().copy());
		});
		conn.waitForClientboundPackets();
		ctx.waitFor(client -> client.gui.screen() instanceof CraftingScreen, 20 * 10);
		ctx.waitTicks(10);
		if (!result.get().is(Items.BANNER.white())) throw new AssertionError(color + " wool and a stick made " + result.get());
		return result.get();
	}

	private static void assertBase(ItemStack stack, String what) {
		var layers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
		if (layers.isEmpty() || !layers.getFirst().pattern().is(ResourceKey.create(Registries.BANNER_PATTERN, CERISE_BASE))) {
			throw new AssertionError(what + " has no Cerise base layer: " + layers);
		}
	}
}

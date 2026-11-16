package se.metacraft.ovvar.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.Enchantment;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;
import se.metacraft.ovvar.content.item.OvveItem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * What a vanilla client sees of the ovve enchantments: the tooltip (names, the curse in red), the Ender Pockets
 * preview and its live update, Keepsake through a death and respawn, and a Magpie ovve on a mannequin (glint on the
 * legs and the top) for Edvin to look at. The curse is given by server code, so this runs without Brewery.
 *
 * Run (cloud only, never on the laptop): {@code ./gradlew :mods:ovvar:runClientGameTest}.
 */
public final class OvvarEnchantmentClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (ClientTestSupport.skipUnderShaderpack("OvvarEnchantmentClientTests")) return;
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25607");   // unique among the client tests
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				ClientTestSupport.acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");

				// Tester holds a Data ovve with Ender Pockets and the curse; the ender chest holds 5 diamonds.
				server.runOnServer(mc -> {
					ServerPlayer tester = tester(mc);
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					ovve.enchant(holder(mc, OvveEnchantments.ENDER_POCKETS), 1);
					ovve.enchant(holder(mc, OvveEnchantments.SPILLED_DRINK_CURSE), 1);
					tester.getInventory().setItem(tester.getInventory().getSelectedSlot(), ovve);
					tester.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
				});
				conn.waitForClientboundPackets();
				ctx.waitTicks(10);

				ctx.runOnClient(client -> {
					ItemStack held = client.player.getMainHandItem();
					List<Component> tooltip = held.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
					if (tooltip.stream().noneMatch(line -> line.getString().contains("Ender Pockets")))
						throw new AssertionError("the tooltip does not name Ender Pockets: " + tooltip);
					Component curse = tooltip.stream().filter(line -> line.getString().contains("Curse of the Spilled Drink")).findFirst()
							.orElseThrow(() -> new AssertionError("the tooltip does not name the curse: " + tooltip));
					// Polymer sends the server tooltip as lore, each line a white, non-italic parent around the
					// enchantment's own (red) name, so the colour that shows is the one the text resolves to.
					java.util.Set<TextColor> shown = new java.util.HashSet<>();
					curse.visit((style, text) -> {
						if (!text.isBlank()) shown.add(style.getColor());
						return java.util.Optional.empty();
					}, Style.EMPTY);
					if (!shown.equals(java.util.Set.of(TextColor.fromLegacyFormat(ChatFormatting.RED))))
						throw new AssertionError("the curse is not red: drawn in " + shown + ", line " + curse);
					int diamonds = count(held, Items.DIAMOND);
					if (diamonds != 5) throw new AssertionError("the client's pockets hold " + diamonds + " diamonds, expected 5");
				});

				// Live update: three emeralds into the ender chest show up within 10 ticks.
				server.runOnServer(mc -> tester(mc).getEnderChestInventory().setItem(1, new ItemStack(Items.EMERALD, 3)));
				ctx.waitTicks(10);
				ctx.runOnClient(client -> {
					int emeralds = count(client.player.getMainHandItem(), Items.EMERALD);
					if (emeralds != 3) throw new AssertionError("the client's pockets hold " + emeralds + " emeralds 10 ticks after the server added 3");
				});

				// Keepsake: the worn ovve survives a death and respawn, in the legs slot.
				server.runOnServer(mc -> {
					ServerPlayer tester = tester(mc);
					tester.getInventory().setItem(tester.getInventory().getSelectedSlot(), ItemStack.EMPTY);
					ItemStack worn = new ItemStack(ModContent.ovve(Chapter.DATA));
					worn.enchant(holder(mc, OvveEnchantments.KEEPSAKE), 1);
					tester.setItemSlot(EquipmentSlot.LEGS, worn);
				});
				server.runCommand("kill Tester");
				ctx.waitTicks(10);
				ctx.runOnClient(client -> client.player.respawn());
				ctx.waitTicks(40);
				boolean kept = server.computeOnServer(mc -> {
					ItemStack legs = tester(mc).getItemBySlot(EquipmentSlot.LEGS);
					return legs.getItem() instanceof OvveItem && OvveEnchantments.levelOn(legs, OvveEnchantments.KEEPSAKE) > 0;
				});
				if (!kept) throw new AssertionError("Tester's ovve is not in the legs slot after the respawn");

				// The picture: a Magpie ovve on a mannequin, top up, so the glint shows on the legs and the top together.
				server.runCommand("tp Tester 0.5 -60 0.5 0 0");
				server.runOnServer(mc -> {
					ServerLevel level = tester(mc).level();
					ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
					ovve.enchant(holder(mc, OvveEnchantments.MAGPIE), 3);
					OvveItem.setTopUp(ovve, true);
					Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, level);
					m.setPos(0.5, -60, 3.5);
					m.setYRot(180); m.setYBodyRot(180); m.setYHeadRot(180);
					m.setItemSlot(EquipmentSlot.LEGS, ovve);
					ItemStack top = new ItemStack(ModContent.top(Chapter.DATA));
					top.set(ModComponents.GARMENT_GLINT, true);
					m.setItemSlot(EquipmentSlot.CHEST, top);
					level.addFreshEntity(m);
				});
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(100);
				Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("enchanted_ovve_front").withSize(1920, 1080));
				BufferedImage img = read(shot);
				int w = img.getWidth(), h = img.getHeight();
				ClientTestSupport.assertNoChecker(img, w / 2 - 150, h / 2 - 40, w / 2 + 150, h / 2 + 185, "enchanted mannequin");
			}
		}
	}

	private static ServerPlayer tester(MinecraftServer mc) {
		return mc.getPlayerList().getPlayerByName("Tester");
	}

	private static net.minecraft.core.Holder<Enchantment> holder(MinecraftServer mc, ResourceKey<Enchantment> key) {
		return mc.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static int count(ItemStack stack, Item item) {
		BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (contents == null) return 0;
		int n = 0;
		for (ItemStack inside : contents.itemCopies().toList()) if (inside.is(item)) n += inside.getCount();
		return n;
	}

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read screenshot " + path, e);
		}
	}
}

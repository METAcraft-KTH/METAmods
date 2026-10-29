package se.metacraft.skins.gametest;

import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import se.metacraft.skins.DrawingCheck;
import se.metacraft.skins.ItemLocation;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.Skins;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static se.metacraft.skins.gametest.SkinFixtures.assertThat;

/**
 * What a viewer is sent of a Skinned Item: Polymer's client copy of the stack, which the Skins
 * module dresses in the Look and the tooltip block while the item is with its Owner, and the
 * drawing check that tells, from the packet being written, who that is. The server's item itself never changes (that
 * is ApplyTests'); whether Polymer's per-viewer pipeline reaches vanilla items at all is checked
 * end to end through the network codec in SkinsCommandTests.
 */
public final class ViewTests {

	/**
	 * While the item is with its Owner every viewer sees its Look. With anyone else, or no one, it's
	 * Away: plain, with one gray line saying whose it is instead of the tooltip block.
	 */
	@GameTest
	public void everyViewerSeesTheLookWhileTheItemIsWithItsOwner(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("candy_sword_test", "sword", "Candy Sword", "Sweet.");
		fx.reload();
		ServerPlayer owner = ApplyTests.carrying(helper, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_SWORD));
		ServerPlayer someoneElse = helper.makeMockServerPlayerInLevel();
		fx.skins.grant(owner.nameAndId(), "candy_sword_test");
		fx.skins.apply(owner, "candy_sword_test", ItemLocation.hotbar(0));
		ItemStack skinned = owner.getInventory().getItem(0);

		for (ServerPlayer viewer : List.of(owner, someoneElse)) {
			assertThat(model(view(helper, fx, skinned, viewer, owner.getUUID())).equals(Identifier.parse("skins:candy_sword_test")), "a viewer sees " + model(view(helper, fx, skinned, viewer, owner.getUUID())));
		}
		String away = "Candy Sword skin, only works for " + owner.getGameProfile().name();
		for (UUID with : Arrays.asList(someoneElse.getUUID(), null)) {
			for (ServerPlayer viewer : List.of(owner, someoneElse)) {
				ItemStack client = view(helper, fx, skinned, viewer, with);
				assertThat(model(client).equals(Identifier.parse("minecraft:diamond_sword")), "with " + with + ", the Away sword shows " + model(client));
				List<Component> lore = lore(client);
				assertLine(lore.getLast(), away, ChatFormatting.GRAY);
				assertThat(lore.stream().noneMatch(l -> l.getString().equals("Obtainable") || l.getString().startsWith("Owned by") || l.getString().equals("Sweet.")),
						"the Away sword keeps the tooltip block: " + text(lore));
			}
		}
		assertThat(model(view(helper, fx, owner.getInventory().getItem(1), owner, owner.getUUID())).equals(Identifier.parse("minecraft:diamond_sword")), "a plain sword has a Look");
		fx.skins.remove(owner.nameAndId(), "candy_sword_test");
		for (UUID with : Arrays.asList(owner.getUUID(), someoneElse.getUUID())) {
			ItemStack stale = view(helper, fx, skinned, someoneElse, with);
			assertThat(model(stale).equals(Identifier.parse("minecraft:diamond_sword")), "a removed skin's item still shows " + model(stale));
			assertThat(lore(stale).stream().noneMatch(l -> l.getString().equals("Obtainable") || l.getString().contains("only works for")),
					"a removed skin's item still says so: " + text(lore(stale)));
		}
		helper.succeed();
	}

	/**
	 * The drawing check reads who the item is with from the packet being written, here bound as the
	 * mod's packet mixin binds it. Working: the Owner's inventory (container 0), cursor and
	 * player-inventory packets, their own slots in an open chest or ender chest, and their equipment
	 * as another player sees it. Away: a chest's slot, someone else's inventory or equipment, an
	 * item entity's or frame's data, a sword inside a bundle the Owner carries, and no packet at all.
	 */
	@GameTest
	public void theDrawingCheckReadsWhoTheItemIsWithFromThePacket(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("candy_sword_test", "sword", "Candy Sword", null).skin("crown_test", "helmet", "Crown", null);
		fx.reload();
		ServerPlayer alice = ApplyTests.carrying(helper, new ItemStack(Items.DIAMOND_SWORD));
		alice.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		ServerPlayer bob = ApplyTests.carrying(helper);
		fx.skins.grant(alice.nameAndId(), "candy_sword_test");
		fx.skins.grant(alice.nameAndId(), "crown_test");
		fx.skins.apply(alice, "candy_sword_test", ItemLocation.hotbar(0));
		fx.skins.apply(alice, "crown_test", ItemLocation.worn(EquipmentSlot.HEAD));
		ItemStack sword = alice.getInventory().getItem(0);
		ItemStack hat = alice.getItemBySlot(EquipmentSlot.HEAD);

		var inventory = new ClientboundContainerSetSlotPacket(0, 0, 36, sword);
		assertWorking(drawnIn(helper, fx, alice, inventory, inventory.getItem()), "the Owner's own inventory slot");
		var cursor = new ClientboundSetCursorItemPacket(sword);
		assertWorking(drawnIn(helper, fx, alice, cursor, cursor.contents()), "the Owner's cursor");
		var playerInventory = new ClientboundSetPlayerInventoryPacket(0, sword);
		assertWorking(drawnIn(helper, fx, alice, playerInventory, playerInventory.contents()), "a player-inventory packet to the Owner");
		var content = new ClientboundContainerSetContentPacket(0, 0, List.of(ItemStack.EMPTY, sword), ItemStack.EMPTY);
		assertWorking(drawnIn(helper, fx, alice, content, content.items().get(1)), "the Owner's whole inventory");
		assertAway(drawnIn(helper, fx, bob, inventory, inventory.getItem()), alice, "someone else's inventory slot");

		// A chest: its own slots are Away, the Owner's inventory below them (from 27; the hotbar from 54) and the cursor work.
		ChestMenu chest = open(alice, ChestMenu.threeRows(alice.containerMenu.containerId + 1, alice.getInventory(), new SimpleContainer(27)));
		var inTheChest = new ClientboundContainerSetSlotPacket(chest.containerId, 0, 0, sword);
		assertAway(drawnIn(helper, fx, alice, inTheChest, inTheChest.getItem()), alice, "a chest slot");
		var belowTheChest = new ClientboundContainerSetSlotPacket(chest.containerId, 0, 54, sword);
		assertWorking(drawnIn(helper, fx, alice, belowTheChest, belowTheChest.getItem()), "the Owner's hotbar below an open chest");
		List<ItemStack> chestSlots = new ArrayList<>(Collections.nCopies(63, ItemStack.EMPTY));
		chestSlots.set(0, sword.copy());
		chestSlots.set(54, sword.copy());
		var chestContent = new ClientboundContainerSetContentPacket(chest.containerId, 0, chestSlots, sword.copy());
		assertAway(drawnIn(helper, fx, alice, chestContent, chestContent.items().get(0)), alice, "a chest slot, the whole chest sent");
		assertWorking(drawnIn(helper, fx, alice, chestContent, chestContent.items().get(54)), "the Owner's hotbar, the whole chest sent");
		assertWorking(drawnIn(helper, fx, alice, chestContent, chestContent.carriedItem()), "the Owner's cursor, the whole chest sent");
		var staleMenu = new ClientboundContainerSetSlotPacket(chest.containerId + 1, 0, 54, sword);
		assertAway(drawnIn(helper, fx, alice, staleMenu, staleMenu.getItem()), alice, "a slot of a menu that isn't open");
		ChestMenu bobsChest = open(bob, ChestMenu.threeRows(bob.containerMenu.containerId + 1, bob.getInventory(), new SimpleContainer(27)));
		var bobsHotbar = new ClientboundContainerSetSlotPacket(bobsChest.containerId, 0, 54, sword);
		assertAway(drawnIn(helper, fx, bob, bobsHotbar, bobsHotbar.getItem()), alice, "someone else's hotbar below their chest");

		ChestMenu enderChest = open(alice, ChestMenu.threeRows(alice.containerMenu.containerId + 1, alice.getInventory(), alice.getEnderChestInventory()));
		var inTheEnderChest = new ClientboundContainerSetSlotPacket(enderChest.containerId, 0, 0, sword);
		assertWorking(drawnIn(helper, fx, alice, inTheEnderChest, inTheEnderChest.getItem()), "the Owner's ender chest");

		// Equipment and entity data, by the entity Polymer ties to the packet.
		var worn = PolymerEntityUtils.setEntityContext(new ClientboundSetEquipmentPacket(alice.getId(), List.of(Pair.of(EquipmentSlot.HEAD, hat))), alice);
		ItemStack seen = drawnIn(helper, fx, bob, worn, worn.getSlots().getFirst().getSecond());
		assertThat(model(seen).equals(Identifier.parse("skins:crown_test")), "another player sees the Owner's hat as " + model(seen));
		var lent = PolymerEntityUtils.setEntityContext(new ClientboundSetEquipmentPacket(bob.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, sword))), bob);
		assertAway(drawnIn(helper, fx, alice, lent, lent.getSlots().getFirst().getSecond()), alice, "the sword in someone else's hand");
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		ItemEntity dropped = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, sword.copy());
		ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), Direction.UP);
		frame.setItem(sword.copy(), false);
		for (Entity entity : List.<Entity>of(dropped, frame)) {
			var data = PolymerEntityUtils.setEntityContext(new ClientboundSetEntityDataPacket(entity.getId(), entity.getEntityData().getNonDefaultValues()), entity);
			ItemStack item = data.packedItems().stream().map(SynchedEntityData.DataValue::value).filter(ItemStack.class::isInstance).map(ItemStack.class::cast)
					.findFirst().orElseThrow(() -> SkinFixtures.fail(entity + " sends no item"));
			assertAway(drawnIn(helper, fx, alice, data, item), alice, "the sword in " + entity.getType().getDescriptionId());
		}

		ItemStack bundle = new ItemStack(Items.BUNDLE);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(sword))));
		var withTheBundle = new ClientboundContainerSetSlotPacket(0, 0, 37, bundle);
		ItemStack inside = withTheBundle.getItem().get(DataComponents.BUNDLE_CONTENTS).itemCopies().findFirst().orElseThrow();
		assertAway(drawnIn(helper, fx, alice, withTheBundle, inside), alice, "a sword inside a bundle the Owner carries");

		PacketContext context = alice.connection.getPacketContext();
		ItemStack unbound = DrawingCheck.draw(fx.skins, sword, polymerCopy(helper, sword, context), context);
		assertAway(unbound, alice, "an encode with no packet bound");
		alice.containerMenu = alice.inventoryMenu;
		bob.containerMenu = bob.inventoryMenu;
		helper.succeed();
	}

	private static ChestMenu open(ServerPlayer player, ChestMenu menu) {
		player.containerMenu = menu;
		return menu;
	}

	private static void assertWorking(ItemStack client, String where) {
		assertThat(model(client).equals(Identifier.parse("skins:candy_sword_test")), where + " shows " + model(client));
		assertThat(lore(client).stream().anyMatch(l -> l.getString().startsWith("Owned by")), where + " lacks the tooltip block: " + text(lore(client)));
	}

	private static void assertAway(ItemStack client, ServerPlayer owner, String where) {
		assertThat(model(client).equals(Identifier.parse("minecraft:diamond_sword")), where + " shows " + model(client));
		List<Component> lore = lore(client);
		assertThat(!lore.isEmpty() && lore.getLast().getString().equals("Candy Sword skin, only works for " + owner.getGameProfile().name())
				&& lore.stream().noneMatch(l -> l.getString().startsWith("Owned by")), where + " reads " + text(lore));
	}

	/**
	 * What this viewer is sent of {@code stack} (the packet's own, or one inside it) while
	 * {@code packet} is being written to them, as the drawing check decides it with the test's Skins.
	 */
	static ItemStack drawnIn(GameTestHelper helper, SkinFixtures fx, ServerPlayer viewer, Packet<?> packet, ItemStack stack) {
		PacketContext context = viewer.connection.getPacketContext();
		return ScopedValue.where(DrawingCheck.PACKET, packet).call(() -> DrawingCheck.draw(fx.skins, stack, polymerCopy(helper, stack, context), context));
	}

	/** Any head armour, turtle shell included, shows the hat on the head and keeps its armour (the server's item is untouched). */
	@GameTest
	public void aHatIsDrawnOnTheHeadOfAnyHelmet(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("crown_test", "helmet");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.TURTLE_HELMET));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		fx.skins.grant(player.nameAndId(), "crown_test");

		for (ItemLocation where : List.of(ItemLocation.hotbar(0), ItemLocation.worn(EquipmentSlot.HEAD))) {
			assertThat(fx.skins.apply(player, "crown_test", where).applied(), "the crown wasn't applied at " + where.label());
			ItemStack helmet = where.in(player.getInventory());
			ItemStack client = view(helper, fx, helmet, player, player.getUUID());
			assertThat(model(client).equals(Identifier.parse("skins:crown_test")), helmet + " shows " + model(client));
			Equippable worn = client.get(DataComponents.EQUIPPABLE);
			assertThat(worn != null && worn.slot() == EquipmentSlot.HEAD && worn.assetId().isEmpty(), helmet + " is sent as equippable " + worn);
			assertThat(Objects.equals(worn.equipSound(), helmet.get(DataComponents.EQUIPPABLE).equipSound()), "the equip sound changed");
			assertThat(helmet.get(DataComponents.EQUIPPABLE).assetId().isPresent(), "the server's helmet lost its armour asset");
			assertThat(Objects.equals(client.get(DataComponents.ATTRIBUTE_MODIFIERS), helmet.get(DataComponents.ATTRIBUTE_MODIFIERS)),
					helmet + " is sent with other armour: " + client.get(DataComponents.ATTRIBUTE_MODIFIERS));
		}
		helper.succeed();
	}

	/** Body armour and the elytra change their hand and inventory look only; worn, they look vanilla. A spear keeps its charge and jab. */
	@GameTest
	public void otherKindsChangeOnlyTheirItemModel(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("plate", "chestplate").skin("wings", "elytra").skin("lance", "spear").skin("hammer", "mace");
		fx.reload();
		ServerPlayer player = ApplyTests.carrying(helper, new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.ELYTRA),
				new ItemStack(Items.IRON_SPEAR), new ItemStack(Items.MACE));
		List<String> skins = List.of("plate", "wings", "lance", "hammer");
		for (int i = 0; i < skins.size(); i++) {
			fx.skins.grant(player.nameAndId(), skins.get(i));
			assertThat(fx.skins.apply(player, skins.get(i), ItemLocation.hotbar(i)).applied(), skins.get(i) + " wasn't applied");
			ItemStack item = player.getInventory().getItem(i);
			ItemStack client = view(helper, fx, item, player, player.getUUID());
			assertThat(model(client).equals(Identifier.fromNamespaceAndPath("skins", skins.get(i))), item + " shows " + model(client));
			assertThat(Objects.equals(client.get(DataComponents.EQUIPPABLE), item.get(DataComponents.EQUIPPABLE)), item + " is worn differently: "
					+ client.get(DataComponents.EQUIPPABLE));
		}
		ItemStack spear = player.getInventory().getItem(2);
		ItemStack client = view(helper, fx, spear, player, player.getUUID());
		assertThat(client.get(DataComponents.KINETIC_WEAPON) != null && Objects.equals(client.get(DataComponents.KINETIC_WEAPON), spear.get(DataComponents.KINETIC_WEAPON)),
				"the spear's charge is sent as " + client.get(DataComponents.KINETIC_WEAPON));
		assertThat(client.get(DataComponents.ATTACK_ANIMATION) != null && Objects.equals(client.get(DataComponents.ATTACK_ANIMATION), spear.get(DataComponents.ATTACK_ANIMATION)),
				"the spear's jab is sent as " + client.get(DataComponents.ATTACK_ANIMATION));
		helper.succeed();
	}

	@GameTest
	public void theTooltipBlockFollowsVanillasLines(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().perky("candy_sword_test", "sword", "Candy Sword", "Sweet, and sharp.", """
				[ { trigger: "held", effects: [ { type: "potion", effect: "minecraft:speed", level: 2 },
				{ type: "attribute", attribute: "minecraft:attack_damage", amount: 0.1, operation: "add_multiplied_base" } ] } ]""")
				.skin("plain", "axe", "Plain Axe", null)
				.file("plain", "skin.json5", "{ name: \"&c&lPlain &#00FF00Axe\", kind: \"axe\", released: \"2026/10/31\", obtainable: false, look: { model: \"plain.json\" } }");
		fx.reload();
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
		ServerPlayer owner = ApplyTests.carrying(helper, sword, new ItemStack(Items.IRON_AXE));
		fx.skins.grant(owner.nameAndId(), "candy_sword_test");
		fx.skins.grant(owner.nameAndId(), "plain");
		fx.skins.apply(owner, "candy_sword_test", ItemLocation.hotbar(0));
		fx.skins.apply(owner, "plain", ItemLocation.hotbar(1));
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		String name = owner.getGameProfile().name();

		ItemStack client = view(helper, fx, owner.getInventory().getItem(0), viewer, owner.getUUID());
		List<Component> lore = lore(client);
		assertThat(lore.size() > 4, "vanilla's lines (the enchantment, the attack damage) are missing: " + text(lore));
		List<Component> block = lore.subList(lore.size() - 4, lore.size());
		assertRelease(block.get(0), "Obtainable", ChatFormatting.GREEN);
		assertLine(block.get(1), "While held: Speed II and +10% Attack Damage", ChatFormatting.BLUE);
		assertLine(block.get(2), "Sweet, and sharp.", ChatFormatting.GRAY);
		assertLine(block.get(3), "Owned by " + name, ChatFormatting.GRAY);
		assertThat(lore.subList(0, lore.size() - 4).stream().anyMatch(l -> l.getString().contains("Sharpness")), "vanilla's lines don't come first: " + text(lore));
		assertThat(client.getHoverName().getString().equals("Old Faithful"), "the item's name became " + client.getHoverName().getString());
		assertThat(Boolean.TRUE.equals(client.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || client.hasFoil(), "the enchanted item lost its glint");

		ItemStack axe = view(helper, fx, owner.getInventory().getItem(1), viewer, owner.getUUID());
		List<Component> axeLore = lore(axe);
		assertRelease(axeLore.get(axeLore.size() - 2), "Released: 2026/10/31 - Unobtainable", ChatFormatting.RED);
		assertLine(axeLore.getLast(), "Owned by " + name, ChatFormatting.GRAY);
		assertThat(!Boolean.TRUE.equals(axe.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "a skin gave the axe a glint");
		// Named after the skin, in its colours, as an item_name: not a custom name, which an anvil would rename the item to.
		assertThat(axe.getHoverName().getString().equals("Plain Axe") && !axe.has(DataComponents.CUSTOM_NAME), "the axe is named " + axe.getHoverName().getString());
		List<Component> words = axe.getHoverName().getSiblings();
		assertThat(words.size() == 2 && words.get(0).getStyle().isBold() && Objects.equals(words.get(0).getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED))
						&& !words.get(1).getStyle().isBold() && Objects.equals(words.get(1).getStyle().getColor(), TextColor.fromRgb(0x00FF00))
						&& !axe.getHoverName().getStyle().isItalic(),
				"the axe's name is styled " + words.stream().map(Component::getStyle).toList());
		helper.succeed();
	}

	/** An offline Owner is named by their last known name: the one they last joined with. */
	@GameTest
	public void anOfflineOwnerIsShownByTheirLastKnownName(GameTestHelper helper) {
		SkinFixtures fx = SkinFixtures.create().skin("candy_sword_test", "sword");
		fx.reload();
		ServerPlayer owner = ApplyTests.carrying(helper, new ItemStack(Items.DIAMOND_SWORD));
		fx.skins.grant(owner.nameAndId(), "candy_sword_test");
		fx.skins.apply(owner, "candy_sword_test", ItemLocation.hotbar(0));
		ItemStack sword = owner.getInventory().getItem(0);
		// They last joined as "Renamed"; the item shows that name whether or not they're online now.
		fx.skins.seen(new NameAndId(owner.getUUID(), "Renamed"));
		List<Component> lore = lore(view(helper, fx, sword, helper.makeMockServerPlayerInLevel(), owner.getUUID()));
		assertLine(lore.getLast(), "Owned by Renamed", ChatFormatting.GRAY);
		helper.succeed();
	}

	/** What Polymer sends this viewer for the server's stack, with {@code with} (null: no one), dressed by the test's Skins. */
	static ItemStack view(GameTestHelper helper, SkinFixtures fx, ItemStack stack, ServerPlayer viewer, @Nullable UUID with) {
		return fx.skins.clientView(stack, polymerCopy(helper, stack, viewer.connection.getPacketContext()), with);
	}

	/**
	 * Polymer's client copy of the stack, before any Skins draws it. The server's own Skins would draw
	 * it first (Polymer runs the mod's callback), and it doesn't know the test's skins, so it would take
	 * them for Dormant (and a non-leather item's colour with them). It's switched off within this call
	 * only, on the server thread, so no other test sees it.
	 */
	static ItemStack polymerCopy(GameTestHelper helper, ItemStack stack, PacketContext context) {
		Skins server = MetacraftSkins.use(null);
		try {
			return PolymerItemUtils.getPolymerItemStack(stack, context, helper.getLevel().registryAccess());
		} finally {
			MetacraftSkins.use(server);
		}
	}

	private static Identifier model(ItemStack client) {
		return client.get(DataComponents.ITEM_MODEL);
	}

	private static List<Component> lore(ItemStack client) {
		return client.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	private static List<String> text(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	/** The release line: its text, ending in the obtainability's colour, and not italic. */
	private static void assertRelease(Component line, String text, ChatFormatting colour) {
		assertThat(line.getString().equals(text), "expected \"" + text + "\", got \"" + line.getString() + "\"");
		assertThat(Objects.equals(line.getSiblings().getLast().getStyle().getColor(), TextColor.fromLegacyFormat(colour)),
				"\"" + text + "\" ends coloured " + line.getSiblings().getLast().getStyle().getColor());
		assertThat(!line.getStyle().isItalic(), "\"" + text + "\" is italic");
	}

	private static void assertLine(Component line, String text, ChatFormatting colour) {
		assertThat(line.getString().equals(text), "expected \"" + text + "\", got \"" + line.getString() + "\"");
		assertThat(Objects.equals(line.getStyle().getColor(), TextColor.fromLegacyFormat(colour)), "\"" + text + "\" is coloured " + line.getStyle().getColor());
		assertThat(!line.getStyle().isItalic(), "\"" + text + "\" is italic");
	}
}

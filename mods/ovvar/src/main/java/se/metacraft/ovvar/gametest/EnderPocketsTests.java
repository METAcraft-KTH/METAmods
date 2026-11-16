package se.metacraft.ovvar.gametest;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import se.metacraft.bundles.util.BundleHelper;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.enchantment.EnderPockets;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Ender Pockets: the ovve's pockets are the clicking player's ender chest. */
public final class EnderPocketsTests {
	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static ItemStack enderOvve(GameTestHelper helper) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		ovve.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(OvveEnchantments.ENDER_POCKETS), 1);
		return ovve;
	}

	private static boolean enderEmpty(ServerPlayer player) {
		return player.getEnderChestInventory().isEmpty();
	}

	private static Set<UUID> itemIds(GameTestHelper helper, Entity at, double radius) {
		Set<UUID> ids = new HashSet<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, at.getBoundingBox().inflate(radius))) ids.add(e.getUUID());
		return ids;
	}

	private static List<ItemEntity> newItemsNear(GameTestHelper helper, Entity at, double radius, Set<UUID> before) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, at.getBoundingBox().inflate(radius))
				.stream().filter(e -> !before.contains(e.getUUID())).toList();
	}

	private static void assertStack(GameTestHelper helper, ItemStack got, net.minecraft.world.item.Item item, int count, String what) {
		if (!got.is(item) || got.getCount() != count) helper.fail(what + ": want " + count + " " + item + ", got " + got);
	}

	private static boolean pocketsEmpty(ItemStack ovve) {
		BundleContents contents = ovve.get(DataComponents.BUNDLE_CONTENTS);
		return contents == null || contents.isEmpty();
	}

	@GameTest
	public void enchantingSpillsThePockets(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		BundleContents.Mutable pockets = ovve.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		pockets.tryInsert(new ItemStack(Items.DIRT, 3));
		ovve.set(DataComponents.BUNDLE_CONTENTS, pockets.toImmutable());
		ovve.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(OvveEnchantments.ENDER_POCKETS), 1);
		player.getInventory().setItem(0, ovve);
		Set<UUID> before = itemIds(helper, player, 4);
		player.getInventory().tick();
		helper.assertTrue(pocketsEmpty(player.getInventory().getItem(0)), "the pockets were emptied: " + player.getInventory().getItem(0).get(DataComponents.BUNDLE_CONTENTS));
		int dirt = 0;
		for (ItemEntity e : newItemsNear(helper, player, 4, before)) if (e.getItem().is(Items.DIRT)) dirt += e.getItem().getCount();
		helper.assertValueEqual(dirt, 3, "dirt thrown on the ground");
		helper.assertTrue(enderEmpty(player), "nothing went into the ender chest");
		helper.succeed();
	}

	@GameTest
	public void clicksGoToTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack ovve = enderOvve(helper);
		player.getInventory().setItem(9, ovve);
		player.inventoryMenu.setCarried(new ItemStack(Items.COBBLESTONE, 16));
		player.inventoryMenu.clicked(9, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the cursor is empty: " + player.inventoryMenu.getCarried());
		assertStack(helper, player.getEnderChestInventory().getItem(0), Items.COBBLESTONE, 16, "ender slot 0");
		helper.assertTrue(pocketsEmpty(player.getInventory().getItem(9)), "the ovve's own pockets stay empty");
		player.inventoryMenu.clicked(9, 1, ContainerInput.PICKUP, player);
		assertStack(helper, player.inventoryMenu.getCarried(), Items.COBBLESTONE, 16, "the cursor after taking out");
		helper.assertTrue(enderEmpty(player), "the ender chest is empty again");
		helper.succeed();
	}

	@GameTest
	public void wornClickGoesToTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.setItemSlot(EquipmentSlot.LEGS, enderOvve(helper));
		player.inventoryMenu.setCarried(new ItemStack(Items.COBBLESTONE, 16));
		player.inventoryMenu.clicked(7, 0, ContainerInput.PICKUP, player);
		assertStack(helper, player.getEnderChestInventory().getItem(0), Items.COBBLESTONE, 16, "ender slot 0");
		helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the cursor is empty");
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.LEGS).is(ModContent.ovve(Chapter.DATA)), "the ovve is still worn");
		helper.succeed();
	}

	@GameTest
	public void twoPlayersTwoChests(GameTestHelper helper) {
		ServerPlayer a = survivor(helper), b = survivor(helper);
		ItemStack ovve = enderOvve(helper);
		a.getInventory().setItem(9, ovve);
		a.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND));
		a.inventoryMenu.clicked(9, 0, ContainerInput.PICKUP, a);
		ItemStack moved = a.getInventory().removeItemNoUpdate(9);
		b.setItemInHand(InteractionHand.MAIN_HAND, moved);
		helper.assertTrue(EnderPockets.view(b, -1).isEmpty(), "B sees B's (empty) ender chest");
		BundleContents aView = EnderPockets.view(a, -1);
		helper.assertValueEqual(aView.size(), 1, "A's view size");
		helper.assertTrue(aView.items().getFirst().item().value() == Items.DIAMOND && aView.items().getFirst().count() == 1, "A's view holds the diamond: " + aView.items());
		helper.assertTrue(enderEmpty(b), "B's ender chest untouched");
		helper.succeed();
	}

	@GameTest
	public void viewMirrorsEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, new ItemStack(Items.DIRT, 5));
		ender.setItem(3, new ItemStack(Items.STONE, 64));
		ender.setItem(26, new ItemStack(Items.APPLE, 1));
		List<ItemStackTemplate> items = EnderPockets.view(player, -1).items();
		helper.assertValueEqual(items.size(), 3, "view size");
		// Each slot as a stand-in of count 1, newest (highest slot) first.
		helper.assertTrue(items.get(0).item().value() == Items.APPLE && items.get(0).count() == 1, "first the apple: " + items);
		helper.assertTrue(items.get(1).item().value() == Items.STONE && items.get(1).count() == 1, "then the stone: " + items);
		helper.assertTrue(items.get(2).item().value() == Items.DIRT && items.get(2).count() == 1, "then the dirt: " + items);
		helper.assertValueEqual(EnderPockets.slotOf(player, 0), 26, "view 0's slot");
		helper.assertValueEqual(EnderPockets.slotOf(player, 3), -1, "view 3's slot");

		ItemStack ovve = enderOvve(helper);
		ovve.set(ModComponents.ENDER_SELECTED, 1);
		player.getInventory().setItem(9, ovve);
		player.inventoryMenu.clicked(9, 1, ContainerInput.PICKUP, player);
		assertStack(helper, player.inventoryMenu.getCarried(), Items.STONE, 64, "the selected stack on the cursor");
		helper.assertTrue(ender.getItem(3).isEmpty(), "ender slot 3 emptied");
		assertStack(helper, ender.getItem(26), Items.APPLE, 1, "ender slot 26 untouched");
		helper.succeed();
	}

	@GameTest
	public void viewDoesNotRecurse(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack held = enderOvve(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, enderOvve(helper));
		ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(enderOvve(helper), new ItemStack(Items.DIRT, 2))));
		ender.setItem(1, shulker);
		PacketContext context = player.connection.getPacketContext();
		var access = helper.getLevel().registryAccess();

		// What the network thread does: encode the stack in this player's packet context. Polymer turns every
		// nested stack into its client form while encoding, so a self-including view would never finish.
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
		try {
			PacketContext.runWithContext(player.connection, () -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, held));
		} catch (StackOverflowError e) {
			helper.fail("encoding the held ovve recursed");
		} finally {
			buf.release();
		}

		ItemStack client = PolymerItemUtils.getPolymerItemStack(held, context, access);
		BundleContents shown = client.get(DataComponents.BUNDLE_CONTENTS);
		helper.assertTrue(shown != null && shown.size() == 2, "the held ovve shows 2 entries: " + shown);
		ItemStack shownShulker = shown.items().get(0).create(), shownOvve = shown.items().get(1).create();
		helper.assertTrue(shownShulker.is(Items.SHULKER_BOX), "newest first: the shulker box: " + shownShulker);
		helper.assertTrue(pocketsEmpty(PolymerItemUtils.getPolymerItemStack(shownOvve, context, access)), "a nested Ender Pockets ovve shows empty pockets");
		ItemStack inShulker = shownShulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
				.filter(s -> s.is(ModContent.ovve(Chapter.DATA))).findFirst().orElse(ItemStack.EMPTY);
		helper.assertFalse(inShulker.isEmpty(), "the shulker box still holds its ovve");
		helper.assertTrue(pocketsEmpty(PolymerItemUtils.getPolymerItemStack(inShulker, context, access)), "an Ender Pockets ovve in a shulker box shows empty pockets");
		helper.succeed();
	}

	@GameTest
	public void polymerStackShowsTheViewersChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
		ItemStack ovve = enderOvve(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, ovve);
		ItemStack client = PolymerItemUtils.getPolymerItemStack(ovve, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		BundleContents shown = client.get(DataComponents.BUNDLE_CONTENTS);
		helper.assertTrue(shown != null && shown.size() == 1 && shown.items().getFirst().item().value() == Items.DIAMOND
				&& shown.items().getFirst().count() == 1, "the client pockets are [diamond stand-in]: " + shown);
		helper.assertTrue(shown.items().getFirst().create().getHoverName().getString().endsWith(" ×5"), "its name tells the count: "
				+ shown.items().getFirst().create().getHoverName().getString());
		helper.succeed();
	}

	// ---- what the client is shown: the icons and the scroll, no fullness

	/** Does any part of {@code line} draw with metacraft-bundles' bar font (the fill bar lore line)? */
	private static boolean drawsABar(Component line) {
		boolean[] bar = {false};
		line.visit((style, text) -> {
			if (style.getFont() instanceof FontDescription.Resource font && font.id().getPath().equals("bundle_textures")) bar[0] = true;
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return bar[0];
	}

	private static List<Component> lore(ItemStack client) {
		return client.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	@GameTest
	public void enderClientStackHasNoFillBar(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, new ItemStack(Items.STONE, 64));
		ender.setItem(1, new ItemStack(Items.DIRT, 5));
		ItemStack ovve = enderOvve(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, ovve);
		ItemStack client = PolymerItemUtils.getPolymerItemStack(ovve, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		helper.assertTrue(client.is(Items.BUNDLE), "the client item is a bundle (tooltip icons, scroll): " + client);
		helper.assertTrue(client.has(DataComponents.BUNDLE_CONTENTS) && client.get(DataComponents.BUNDLE_CONTENTS).size() == 2,
				"the client stack carries the icons: " + client.get(DataComponents.BUNDLE_CONTENTS));
		helper.assertFalse(lore(client).stream().anyMatch(EnderPocketsTests::drawsABar), "no fill bar line: " + lore(client));
		helper.succeed();
	}

	@GameTest
	public void normalOvveKeepsItsFillBar(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.DATA));
		BundleContents.Mutable pockets = ovve.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		pockets.tryInsert(new ItemStack(Items.DIRT, 32));
		ovve.set(DataComponents.BUNDLE_CONTENTS, pockets.toImmutable());
		player.setItemInHand(InteractionHand.MAIN_HAND, ovve);
		ItemStack client = PolymerItemUtils.getPolymerItemStack(ovve, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		Component want = BundleHelper.getOccupancyText(ovve);
		helper.assertTrue(lore(client).contains(want), "the normal ovve keeps its fill bar " + want + ": " + lore(client));
		helper.assertTrue(client.get(DataComponents.BUNDLE_CONTENTS).size() == 1, "and its own pockets: " + client.get(DataComponents.BUNDLE_CONTENTS));
		helper.succeed();
	}

	// ---- the real click packet, from a vanilla client that predicted a full bundle

	/** What a vanilla client sends for a click: its predicted cursor, which (for a "full" bundle) is still the stack it holds. */
	private static void clickPacket(ServerPlayer player, int slot, int button, ItemStack predictedCursor) {
		var menu = player.containerMenu;
		player.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
				(short) slot, (byte) button, ContainerInput.PICKUP, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(),
				predictedCursor.isEmpty() ? net.minecraft.network.HashedStack.EMPTY : net.minecraft.network.HashedStack.create(predictedCursor, c -> 0)));
	}

	@GameTest
	public void insertFillsPartialsThenEmptySlotsUpTo27Stacks(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, new ItemStack(Items.STONE, 64));
		ender.setItem(5, new ItemStack(Items.DIRT, 40));
		ender.setItem(9, new ItemStack(Items.DIRT, 10));
		player.getInventory().setItem(9, enderOvve(helper));
		Set<UUID> before = itemIds(helper, player, 4);
		int given = 0;

		// One stack of 64 already fills a vanilla bundle: the client predicts a refusal, the server takes it anyway.
		player.inventoryMenu.setCarried(new ItemStack(Items.DIRT, 64));
		given += 64;
		clickPacket(player, 9, 0, player.inventoryMenu.getCarried().copy());
		helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the first 64 went in: " + player.inventoryMenu.getCarried());
		assertStack(helper, ender.getItem(5), Items.DIRT, 64, "partial slot 5 filled first");
		assertStack(helper, ender.getItem(9), Items.DIRT, 50, "then partial slot 9");
		helper.assertTrue(ender.getItem(1).isEmpty(), "no empty slot used while partials had room");

		// Then the empty slots, until 27 full stacks.
		for (int i = 0; i < 30 && ender.getItems().stream().anyMatch(s -> s.isEmpty() || s.getCount() < 64); i++) {
			player.inventoryMenu.setCarried(new ItemStack(Items.DIRT, 64));
			given += 64;
			clickPacket(player, 9, 0, player.inventoryMenu.getCarried().copy());
		}
		for (int slot = 0; slot < 27; slot++) helper.assertValueEqual(ender.getItem(slot).getCount(), 64, "ender slot " + slot + " full");
		int left = player.inventoryMenu.getCarried().getCount();
		helper.assertValueEqual(left, given - 64 * 26 + 50, "what did not fit stays on the cursor");

		// Full: refused, nothing moves.
		clickPacket(player, 9, 0, player.inventoryMenu.getCarried().copy());
		helper.assertValueEqual(player.inventoryMenu.getCarried().getCount(), left, "a full chest refuses");
		helper.assertValueEqual(total(helper, player, Items.DIRT, before), given + 50, "dirt conserved");
		helper.succeed();
	}

	@GameTest
	public void takeOutByPacketResendsSlotAndCursor(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(4, new ItemStack(Items.APPLE, 3));
		ender.setItem(7, new ItemStack(Items.STONE, 64));
		ender.setItem(8, new ItemStack(Items.DIRT, 64));
		player.getInventory().setItem(9, enderOvve(helper));
		var channel = channel(helper, player);
		player.containerMenu.broadcastChanges();
		sentSlots(helper, player, channel, 0, 9);

		clickPacket(player, 9, 1, ItemStack.EMPTY);
		assertStack(helper, player.inventoryMenu.getCarried(), Items.DIRT, 64, "the newest stack on the cursor");
		helper.assertTrue(ender.getItem(8).isEmpty(), "ender slot 8 emptied");
		helper.runAfterDelay(1, () -> {
			channel.runPendingTasks();
			ItemStack cursor = null, slot = null;
			Object packet;
			while ((packet = channel.readOutbound()) != null) {
				if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket set) cursor = set.contents();
				if (packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket set && set.getContainerId() == 0 && set.getSlot() == 9)
					slot = PolymerItemUtils.getPolymerItemStack(set.getItem(), player.connection.getPacketContext(), helper.getLevel().registryAccess());
			}
			helper.assertTrue(cursor != null && cursor.is(Items.DIRT) && cursor.getCount() == 64, "the client is told its cursor holds the dirt: " + cursor);
			helper.assertTrue(slot != null && shown(slot, Items.DIRT) == 0 && shown(slot, Items.STONE) == 1 && shown(slot, Items.APPLE) == 1,
					"the client is sent the ovve without the dirt: " + (slot == null ? null : slot.get(DataComponents.BUNDLE_CONTENTS)));
			helper.succeed();
		});
	}

	// ---- creative: the client's inventory is its own, it sends back the ovve as it changed it

	private static ServerPlayer creative(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		return player;
	}

	/** The ovve in inventory slot 9 as this player's client has it, with its pockets changed the way the client changed them. */
	private static ItemStack clientEdited(GameTestHelper helper, ServerPlayer player, java.util.function.UnaryOperator<List<ItemStackTemplate>> edit) {
		ItemStack client = PolymerItemUtils.getPolymerItemStack(player.getInventory().getItem(9), player.connection.getPacketContext(), helper.getLevel().registryAccess());
		List<ItemStackTemplate> items = edit.apply(new java.util.ArrayList<>(client.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items()));
		client.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
		return client;
	}

	/** The creative slot packet as the server decodes it (Polymer turns the client stack back into the server's) and handles it. */
	private static void sendCreative(GameTestHelper helper, ServerPlayer player, ItemStack client) {
		ItemStack real = PolymerItemUtils.getRealItemStack(client, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		player.connection.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(9, real));
	}

	@GameTest
	public void creativeInsertGoesToTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = creative(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, new ItemStack(Items.DIAMOND, 5));
		player.getInventory().setItem(9, enderOvve(helper));
		ItemStack client = clientEdited(helper, player, items -> {
			items.addFirst(new ItemStackTemplate(Items.EMERALD, 3));
			return items;
		});
		sendCreative(helper, player, client);
		helper.runAfterDelay(1, () -> {
			assertStack(helper, ender.getItem(0), Items.DIAMOND, 5, "the diamonds stay");
			int emeralds = ender.getItems().stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum();
			helper.assertValueEqual(emeralds, 3, "the emeralds went into the ender chest");
			helper.assertTrue(pocketsEmpty(player.getInventory().getItem(9)), "the ovve's own pockets stay empty");
			helper.succeed();
		});
	}

	/** A creative slot packet for inventory slot {@code slot}, decoded and handled as the server does. */
	private static void sendCreative(GameTestHelper helper, ServerPlayer player, int slot, ItemStack client) {
		ItemStack real = PolymerItemUtils.getRealItemStack(client, player.connection.getPacketContext(), helper.getLevel().registryAccess());
		player.connection.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(slot, real));
	}

	@GameTest
	public void creativeTakeOutLeavesTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = creative(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(2, new ItemStack(Items.APPLE, 3));
		ender.setItem(5, new ItemStack(Items.STONE, 10));
		player.getInventory().setItem(9, enderOvve(helper));
		// The client's removeOne: the first entry (the newest, ender slot 5) goes onto its cursor as the stand-in.
		ItemStack[] cursor = new ItemStack[1];
		ItemStack client = clientEdited(helper, player, items -> {
			cursor[0] = items.removeFirst().create();
			return items;
		});
		sendCreative(helper, player, client);
		helper.runAfterDelay(1, () -> {
			assertStack(helper, ender.getItem(5), Items.STONE, 10, "the stone stays until the stand-in lands somewhere");
			// The client puts its cursor down in slot 20: the real stack lands there and leaves the chest.
			sendCreative(helper, player, 20, cursor[0]);
			assertStack(helper, player.getInventory().getItem(20), Items.STONE, 10, "the real stone in slot 20");
			helper.assertTrue(player.getInventory().getItem(20).get(DataComponents.MAX_STACK_SIZE) == 64, "a real stone, not the stand-in");
			helper.assertTrue(ender.getItem(5).isEmpty(), "the stone left the ender chest: " + ender.getItem(5));
			assertStack(helper, ender.getItem(2), Items.APPLE, 3, "the apples stay");
			// The same stand-in again (a client that had not been told yet) is nothing: no second stone.
			sendCreative(helper, player, 21, cursor[0]);
			helper.assertTrue(player.getInventory().getItem(21).isEmpty(), "a stale stand-in is nothing: " + player.getInventory().getItem(21));
			helper.succeed();
		});
	}

	@GameTest
	public void creativeTakeOutOfAModdedItemIsTheRealOne(GameTestHelper helper) {
		ServerPlayer player = creative(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ItemStack plain = new ItemStack(ModContent.ovve(Chapter.IT));
		java.util.UUID owner = java.util.UUID.randomUUID();
		se.metacraft.ovvar.content.item.OvveItem.setOwner(plain, owner);
		ender.setItem(0, plain.copy());
		player.getInventory().setItem(9, enderOvve(helper));
		ItemStack[] cursor = new ItemStack[1];
		sendCreative(helper, player, clientEdited(helper, player, items -> {
			cursor[0] = items.removeFirst().create();
			return items;
		}));
		helper.assertTrue(cursor[0].is(Items.BUNDLE), "the stand-in is the ovve's client form: " + cursor[0]);
		sendCreative(helper, player, 20, cursor[0]);
		ItemStack landed = player.getInventory().getItem(20);
		helper.assertTrue(ItemStack.matches(landed, plain), "the real ovve, owner and all: " + landed);
		helper.assertTrue(ender.getItem(0).isEmpty(), "and it left the chest");
		helper.succeed();
	}

	@GameTest
	public void standInsNeverBecomeItems(GameTestHelper helper) {
		ServerPlayer player = creative(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(3, new ItemStack(Items.DIAMOND, 7));
		player.getInventory().setItem(9, enderOvve(helper));
		ItemStack standIn = clientEdited(helper, player, items -> items).get(DataComponents.BUNDLE_CONTENTS).items().getFirst().create();
		// The chest changed since: the stand-in no longer shows what is in its slot.
		ender.setItem(3, new ItemStack(Items.DIAMOND, 6));
		sendCreative(helper, player, 20, standIn);
		helper.assertTrue(player.getInventory().getItem(20).isEmpty(), "a stand-in for a stack that is not there is nothing");
		// One hidden in a shulker box is taken out of it.
		ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(standIn.copy(), new ItemStack(Items.DIRT, 2))));
		sendCreative(helper, player, 21, shulker);
		List<ItemStack> inside = player.getInventory().getItem(21).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
				.nonEmptyItemCopyStream().toList();
		helper.assertTrue(inside.size() == 1 && inside.getFirst().is(Items.DIRT), "only the dirt is left in the box: " + inside);
		assertStack(helper, ender.getItem(3), Items.DIAMOND, 6, "the diamonds stay");
		helper.succeed();
	}

	// ---- what a vanilla 26.3 client decides about the pockets it is shown

	/**
	 * Vanilla's bundle weight of a client stack's contents, as the 26.3 client computes it
	 * ({@code BundleContents.computeContentWeight} / {@code getWeight}): per entry 1/16 plus its own contents for a
	 * bundle, 1 for a beehive with bees, else 1/max stack size, times the count. Re-done here because on this server
	 * {@code BundleContents} carries metacraft-bundles' size factor, which a vanilla client does not have.
	 */
	private static org.apache.commons.lang3.math.Fraction vanillaWeight(ItemStack client) {
		org.apache.commons.lang3.math.Fraction total = org.apache.commons.lang3.math.Fraction.ZERO;
		for (ItemStackTemplate entry : client.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items()) {
			ItemStack e = entry.create();
			org.apache.commons.lang3.math.Fraction w;
			if (e.get(DataComponents.BUNDLE_CONTENTS) != null) w = org.apache.commons.lang3.math.Fraction.getFraction(1, 16).add(vanillaWeight(e));
			else if (!e.getOrDefault(DataComponents.BEES, net.minecraft.world.item.component.Bees.EMPTY).bees().isEmpty()) w = org.apache.commons.lang3.math.Fraction.ONE;
			else w = org.apache.commons.lang3.math.Fraction.getFraction(1, e.getMaxStackSize());
			total = total.add(w.multiplyBy(org.apache.commons.lang3.math.Fraction.getFraction(entry.count(), 1)));
		}
		return total;
	}

	/** How many of {@code cursor} a vanilla client's {@code BundleContents.Mutable.tryInsert} puts into pockets of this weight. */
	private static int vanillaInsertable(org.apache.commons.lang3.math.Fraction weight, ItemStack cursor) {
		int room = org.apache.commons.lang3.math.Fraction.ONE.subtract(weight).multiplyBy(org.apache.commons.lang3.math.Fraction.getFraction(cursor.getMaxStackSize(), 1)).intValue();
		return Math.min(cursor.getCount(), Math.max(room, 0));
	}

	/** Vanilla's {@code BundleItem.getBarWidth}: 1 + weight × 12, at most 13 (13 is a full bar, drawn red). */
	private static int vanillaBarWidth(org.apache.commons.lang3.math.Fraction weight) {
		return Math.min(1 + weight.multiplyBy(org.apache.commons.lang3.math.Fraction.getFraction(12, 1)).intValue(), 13);
	}

	/** The ovve in inventory slot 9 as this player's client gets it, nested entries in their client form as the encoder leaves them. */
	private static ItemStack asTheClientGetsIt(GameTestHelper helper, ServerPlayer player) {
		var context = player.connection.getPacketContext();
		var access = helper.getLevel().registryAccess();
		ItemStack client = PolymerItemUtils.getPolymerItemStack(player.getInventory().getItem(9), context, access);
		List<ItemStackTemplate> entries = new java.util.ArrayList<>();
		for (ItemStackTemplate entry : client.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items()) {
			ItemStack once = entry.create();
			helper.assertFalse(PolymerItemUtils.isPolymerServerItem(once, context), "a stand-in is not converted again: " + once);
			entries.add(ItemStackTemplate.fromNonEmptyStack(PolymerItemUtils.getPolymerItemStack(once, context, access)));
		}
		client.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(entries));
		return client;
	}

	@GameTest
	public void aVanillaClientNeverSeesThePocketsFull(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		for (int slot = 0; slot < 5; slot++) ender.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		ender.setItem(5, new ItemStack(Items.DIAMOND_SWORD));
		ender.setItem(6, new ItemStack(Items.SHULKER_BOX));
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(Items.DIRT, 64))));
		ender.setItem(7, bundle);
		ItemStack beehive = new ItemStack(Items.BEEHIVE);
		beehive.set(DataComponents.BEES, new net.minecraft.world.item.component.Bees(List.of(net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant.create(1))));
		ender.setItem(8, beehive);
		ItemStack plain = new ItemStack(ModContent.ovve(Chapter.DATA));
		BundleContents.Mutable pockets = plain.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
		pockets.tryInsert(new ItemStack(Items.DIRT, 64));
		plain.set(DataComponents.BUNDLE_CONTENTS, pockets.toImmutable());
		ender.setItem(9, plain);
		player.getInventory().setItem(9, enderOvve(helper));

		ItemStack client = asTheClientGetsIt(helper, player);
		helper.assertValueEqual(client.get(DataComponents.BUNDLE_CONTENTS).size(), 10, "one entry per ender slot");
		var weight = vanillaWeight(client);
		helper.assertTrue(weight.compareTo(org.apache.commons.lang3.math.Fraction.ONE) < 0, "a vanilla client sees the pockets under full: " + weight);
		ItemStack cobble = new ItemStack(Items.COBBLESTONE, 64);
		helper.assertTrue(vanillaInsertable(weight, cobble) > 0, "so it lets 64 cobblestone in: " + vanillaInsertable(weight, cobble) + " at " + weight);
		helper.assertTrue(vanillaInsertable(weight, new ItemStack(Items.ENDER_PEARL, 16)) > 0, "and ender pearls");

		// The whole chest full: still a sliver of a bar, never red, and room for a stack.
		for (int slot = 0; slot < 27; slot++) ender.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		weight = vanillaWeight(asTheClientGetsIt(helper, player));
		helper.assertTrue(weight.compareTo(org.apache.commons.lang3.math.Fraction.getFraction(27, 99)) == 0, "27 stand-ins weigh 27/99: " + weight);
		helper.assertValueEqual(vanillaBarWidth(weight), 4, "the bar is 4 pixels of 13");
		helper.assertTrue(vanillaInsertable(weight, cobble) > 0, "and a stack still goes in: " + vanillaInsertable(weight, cobble));
		helper.succeed();
	}

	@GameTest
	public void fiveStacksOfTheSameItemThenMoreInSurvival(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		for (int slot = 0; slot < 5; slot++) ender.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		player.getInventory().setItem(9, enderOvve(helper));
		ItemStack carried = new ItemStack(Items.COBBLESTONE, 64);
		helper.assertTrue(vanillaInsertable(vanillaWeight(asTheClientGetsIt(helper, player)), carried) > 0, "the vanilla client predicts an insert");
		player.inventoryMenu.setCarried(carried);
		clickPacket(player, 9, 0, ItemStack.EMPTY);
		helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "the 6th stack went in: " + player.inventoryMenu.getCarried());
		helper.assertValueEqual(ender.getItems().stream().filter(s -> s.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum(), 6 * 64, "6 stacks in the chest");
		helper.succeed();
	}

	@GameTest
	public void fiveStacksOfTheSameItemThenMoreInCreative(GameTestHelper helper) {
		ServerPlayer player = creative(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		for (int slot = 0; slot < 5; slot++) ender.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		player.getInventory().setItem(9, enderOvve(helper));
		ItemStack client = asTheClientGetsIt(helper, player);
		ItemStack carried = new ItemStack(Items.COBBLESTONE, 64);
		int n = vanillaInsertable(vanillaWeight(client), carried);
		helper.assertTrue(n > 0, "the vanilla client inserts in creative (it would refuse at weight 1)");
		// What vanilla's tryInsert does with no matching entry: the inserted part goes in as entry 0.
		List<ItemStackTemplate> items = new java.util.ArrayList<>(client.get(DataComponents.BUNDLE_CONTENTS).items());
		items.addFirst(new ItemStackTemplate(Items.COBBLESTONE, n));
		client.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
		sendCreative(helper, player, client);
		// The same packet again (the client had not been told the new chest yet) adds nothing more.
		sendCreative(helper, player, client.copy());
		helper.runAfterDelay(2, () -> {
			helper.assertValueEqual(ender.getItems().stream().filter(s -> s.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum(), 5 * 64 + n,
					"the inserted cobblestone is in the chest, once");
			helper.assertTrue(pocketsEmpty(player.getInventory().getItem(9)), "the ovve's own pockets stay empty");
			helper.succeed();
		});
	}

	@GameTest
	public void creativeStaleViewChangesNothing(GameTestHelper helper) {
		ServerPlayer player = creative(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, new ItemStack(Items.DIAMOND, 5));
		player.getInventory().setItem(9, enderOvve(helper));
		ItemStack client = clientEdited(helper, player, items -> items);
		// The chest changes before the client has seen it: its ovve, sent back unchanged, must not "take out" the new stack.
		ender.setItem(1, new ItemStack(Items.EMERALD, 3));
		sendCreative(helper, player, client);
		helper.runAfterDelay(1, () -> {
			assertStack(helper, ender.getItem(0), Items.DIAMOND, 5, "the diamonds stay");
			assertStack(helper, ender.getItem(1), Items.EMERALD, 3, "the emeralds stay");
			helper.succeed();
		});
	}

	@GameTest
	public void survivalCreativePacketChangesNothing(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(0, new ItemStack(Items.DIAMOND, 5));
		player.getInventory().setItem(9, enderOvve(helper));
		ItemStack client = clientEdited(helper, player, items -> {
			items.addFirst(new ItemStackTemplate(Items.EMERALD, 3));
			return items;
		});
		sendCreative(helper, player, client);
		helper.runAfterDelay(1, () -> {
			helper.assertFalse(ender.getItems().stream().anyMatch(s -> s.is(Items.EMERALD)), "no emeralds from a survival player's packet");
			helper.succeed();
		});
	}

	// ---- the ovve on the cursor (stackedOnOther), every item counted before and after

	/** Every item of {@code item} the player has anywhere: inventory, cursor, ender chest, and on the ground near them since {@code before}. */
	private static int total(GameTestHelper helper, ServerPlayer player, net.minecraft.world.item.Item item, Set<UUID> before) {
		int n = player.inventoryMenu.getCarried().is(item) ? player.inventoryMenu.getCarried().getCount() : 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		for (ItemStack s : player.getEnderChestInventory().getItems()) if (s.is(item)) n += s.getCount();
		for (ItemEntity e : newItemsNear(helper, player, 4, before)) if (e.getItem().is(item)) n += e.getItem().getCount();
		return n;
	}

	@GameTest
	public void cursorPrimaryMovesOnlyWhatFits(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		for (int slot = 1; slot < ender.getContainerSize(); slot++) ender.setItem(slot, new ItemStack(Items.STONE, 64));
		ender.setItem(0, new ItemStack(Items.DIRT, 40));
		player.getInventory().setItem(9, new ItemStack(Items.DIRT, 64));
		player.inventoryMenu.setCarried(enderOvve(helper));
		Set<UUID> before = itemIds(helper, player, 4);
		int dirt = total(helper, player, Items.DIRT, before), stone = total(helper, player, Items.STONE, before);
		player.inventoryMenu.clicked(9, 0, ContainerInput.PICKUP, player);
		assertStack(helper, ender.getItem(0), Items.DIRT, 64, "ender slot 0 filled up");
		assertStack(helper, player.getInventory().getItem(9), Items.DIRT, 40, "the rest stays in the slot");
		helper.assertTrue(EnderPockets.has(player.inventoryMenu.getCarried()), "the ovve is still on the cursor: " + player.inventoryMenu.getCarried());
		helper.assertValueEqual(total(helper, player, Items.DIRT, before), dirt, "dirt conserved");
		helper.assertValueEqual(total(helper, player, Items.STONE, before), stone, "stone conserved");
		helper.succeed();
	}

	@GameTest
	public void cursorSecondaryTakesTheSelectedStack(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(2, new ItemStack(Items.APPLE, 3));
		ender.setItem(5, new ItemStack(Items.STONE, 10));
		Set<UUID> before = itemIds(helper, player, 4);
		int apples = total(helper, player, Items.APPLE, before), stone = total(helper, player, Items.STONE, before);

		// Nothing selected: the newest (ender slot 5) lands in the empty slot.
		player.inventoryMenu.setCarried(enderOvve(helper));
		player.inventoryMenu.clicked(10, 1, ContainerInput.PICKUP, player);
		assertStack(helper, player.getInventory().getItem(10), Items.STONE, 10, "the newest stack in slot 10");
		helper.assertTrue(ender.getItem(5).isEmpty(), "ender slot 5 emptied");

		// The view is [dirt (slot 5), apples (slot 2)]: index 1 selects the apples.
		player.getEnderChestInventory().setItem(5, new ItemStack(Items.DIRT, 1));
		player.inventoryMenu.getCarried().set(ModComponents.ENDER_SELECTED, 1);
		player.inventoryMenu.clicked(11, 1, ContainerInput.PICKUP, player);
		assertStack(helper, player.getInventory().getItem(11), Items.APPLE, 3, "the selected stack in slot 11");
		helper.assertTrue(ender.getItem(2).isEmpty(), "ender slot 2 emptied");
		assertStack(helper, ender.getItem(5), Items.DIRT, 1, "the unselected stack stays");
		helper.assertFalse(player.inventoryMenu.getCarried().has(ModComponents.ENDER_SELECTED), "the selection is cleared");
		helper.assertTrue(EnderPockets.has(player.inventoryMenu.getCarried()), "the ovve is still on the cursor");
		helper.assertValueEqual(total(helper, player, Items.APPLE, before), apples, "apples conserved");
		helper.assertValueEqual(total(helper, player, Items.STONE, before), stone, "stone conserved");
		helper.succeed();
	}

	@GameTest
	public void cursorSecondaryStaleSelectionFallsBack(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(4, new ItemStack(Items.APPLE, 2));
		ItemStack ovve = enderOvve(helper);
		// Selected in a view that had more entries than the chest has now.
		ovve.set(ModComponents.ENDER_SELECTED, 5);
		player.inventoryMenu.setCarried(ovve);
		player.inventoryMenu.clicked(10, 1, ContainerInput.PICKUP, player);
		assertStack(helper, player.getInventory().getItem(10), Items.APPLE, 2, "the newest stack instead");
		helper.assertTrue(ender.isEmpty(), "the ender chest is empty");
		helper.succeed();
	}

	@GameTest
	public void cursorOntoARefusingSlotMovesNothing(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ender.setItem(5, new ItemStack(Items.STONE, 10));
		Set<UUID> before = itemIds(helper, player, 4);
		int stone = total(helper, player, Items.STONE, before);

		// The feet slot (inventory menu slot 8) refuses stone.
		player.inventoryMenu.setCarried(enderOvve(helper));
		player.inventoryMenu.clicked(8, 1, ContainerInput.PICKUP, player);
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "the feet slot stays empty: " + player.getItemBySlot(EquipmentSlot.FEET));
		assertStack(helper, ender.getItem(5), Items.STONE, 10, "the stone stays in ender slot 5");
		helper.assertTrue(EnderPockets.has(player.inventoryMenu.getCarried()), "the ovve is still on the cursor");
		helper.assertValueEqual(total(helper, player, Items.STONE, before), stone, "stone conserved");

		// The crafting result slot (inventory menu slot 0) takes nothing put in.
		player.inventoryMenu.clicked(0, 1, ContainerInput.PICKUP, player);
		helper.assertTrue(player.inventoryMenu.getSlot(0).getItem().isEmpty(), "the result slot stays empty: " + player.inventoryMenu.getSlot(0).getItem());
		assertStack(helper, ender.getItem(5), Items.STONE, 10, "the ender chest unchanged");
		helper.assertValueEqual(total(helper, player, Items.STONE, before), stone, "stone still conserved");
		helper.assertValueEqual((int) ender.getItems().stream().filter(s -> !s.isEmpty()).count(), 1, "one stack in the ender chest");
		helper.succeed();
	}

	// ---- the preview follows the ender chest

	private static io.netty.channel.embedded.EmbeddedChannel channel(GameTestHelper helper, ServerPlayer player) {
		try {
			var field = net.minecraft.network.Connection.class.getDeclaredField("channel");
			field.setAccessible(true);
			var connection = net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
			connection.setAccessible(true);
			return (io.netty.channel.embedded.EmbeddedChannel) field.get(connection.get(player.connection));
		} catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
			helper.fail("can't reach the mock player's channel: " + e);
			return null;
		}
	}

	/** The slot packets for {@code slot} of menu {@code containerId} sent since the last call, as the client would see them. */
	private static List<ItemStack> sentSlots(GameTestHelper helper, ServerPlayer player, io.netty.channel.embedded.EmbeddedChannel channel, int containerId, int slot) {
		List<ItemStack> sent = new java.util.ArrayList<>();
		channel.runPendingTasks();
		Object packet;
		while ((packet = channel.readOutbound()) != null) {
			if (packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket set && set.getContainerId() == containerId && set.getSlot() == slot) {
				sent.add(PolymerItemUtils.getPolymerItemStack(set.getItem(), player.connection.getPacketContext(), helper.getLevel().registryAccess()));
			}
		}
		return sent;
	}

	/** How many entries of {@code item} the client's pockets show: one stand-in per ender slot. */
	private static int shown(ItemStack client, net.minecraft.world.item.Item item) {
		BundleContents shown = client.get(DataComponents.BUNDLE_CONTENTS);
		if (shown == null) return 0;
		int n = 0;
		for (ItemStackTemplate t : shown.items()) if (t.item().value() == item) n++;
		return n;
	}

	/**
	 * The server's ovve never changes when the ender chest does, so vanilla's (and Polymer's) slot sync never
	 * resends it on its own: EnderPockets watches the chests of players carrying one and resends those slots.
	 */
	@GameTest(maxTicks = 40)
	public void enderChangesReachThePreview(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.getInventory().setItem(9, enderOvve(helper));
		player.setItemSlot(EquipmentSlot.LEGS, enderOvve(helper));
		var channel = channel(helper, player);
		player.containerMenu.broadcastChanges();
		sentSlots(helper, player, channel, 0, 9);
		helper.runAfterDelay(2, () -> {
			player.containerMenu.broadcastChanges();
			sentSlots(helper, player, channel, 0, 9);
			player.getEnderChestInventory().setItem(1, new ItemStack(Items.EMERALD, 3));
			int[] seen = new int[2];
			helper.succeedWhen(() -> {
				channel.runPendingTasks();
				Object packet;
				while ((packet = channel.readOutbound()) != null) {
					if (packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket set && set.getContainerId() == 0) {
						ItemStack client = PolymerItemUtils.getPolymerItemStack(set.getItem(), player.connection.getPacketContext(), helper.getLevel().registryAccess());
						if (set.getSlot() == 9 && shown(client, Items.EMERALD) == 1) seen[0] = 1;
						if (set.getSlot() == 7 && shown(client, Items.EMERALD) == 1) seen[1] = 1;
					}
				}
				helper.assertTrue(seen[0] == 1, "the carried ovve's slot was resent with the emeralds");
				helper.assertTrue(seen[1] == 1, "the worn ovve's slot was resent with the emeralds");
			});
		});
	}

	@GameTest
	public void useOpensTheEnderChest(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack ovve = enderOvve(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, ovve);
		Set<UUID> before = itemIds(helper, player, 4);
		InteractionResult result = ovve.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		// ItemStack.use records the held stack on the result, so this is SUCCESS with that filled in.
		helper.assertTrue(result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SUCCESS.swingSource(),
				"use succeeds: " + result);
		helper.assertTrue(player.containerMenu instanceof ChestMenu chest && chest.getContainer() == player.getEnderChestInventory(),
				"the open menu is the ender chest: " + player.containerMenu);
		helper.assertTrue(newItemsNear(helper, player, 4, before).isEmpty(), "nothing dropped");
		helper.succeed();
	}
}

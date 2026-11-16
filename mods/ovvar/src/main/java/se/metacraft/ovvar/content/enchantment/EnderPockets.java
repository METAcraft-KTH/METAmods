package se.metacraft.ovvar.content.enchantment;

import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;
import se.metacraft.bundles.util.BundleHelper;
import se.metacraft.lib.util.TaskScheduler;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.item.OvveItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ender Pockets: an ovve whose pockets are the ender chest of whoever uses them. Clicking an item onto
 * it puts the item in the clicking player's ender chest; right-clicking it with an empty cursor takes a
 * whole stack out; using it opens the ender chest screen. Its own {@code bundle_contents} stay empty:
 * whatever was in them when it got the enchantment is thrown on the ground ({@link #spill}) the next
 * time it is touched or ticked, never moved into anybody's ender chest.
 *
 * The client is shown the viewer's own ender chest as the bundle's contents ({@link #view}), newest
 * first like a bundle: the item icons and the scroll, as stand-ins of count 1 that weigh almost nothing, so a
 * vanilla client never sees the pockets full, and no fill bar line ({@link #dropFillBar}). A preview never contains itself: an
 * Ender Pockets ovve inside the preview (in the ender chest, or in a shulker box or bundle there) is
 * handed over marked {@link ModComponents#ENDER_NESTED} and shows empty pockets. A marker, not only a
 * flag for the duration of the call, because Polymer turns nested stacks into client stacks later,
 * while the packet is encoded on the network thread, after {@link #clientStack} has returned.
 *
 * The server's ovve does not change when the ender chest does, so the menus' slot sync (vanilla's, and
 * Polymer's, which compares server stacks once a slot has been sent) never resends it by itself. So
 * {@link #tick} keeps a copy of the ender chest of every player who has an Ender Pockets ovve in an open
 * menu or on the cursor, and when it differs, or a preview read tore, resends those slots ({@link #resend}).
 *
 * A vanilla client decides its bundle clicks itself. In survival that is only its prediction: the click packet
 * comes whatever it predicted, the server puts the stack in the chest, and {@link #afterClick} resends the slot and
 * the cursor. In creative the client's inventory is its own: it refuses an insert into a bundle it thinks is full
 * before anything is sent, which is why the stand-ins weigh so little, and otherwise sends back the ovve as it
 * changed it, which Polymer turns back into the server's ovve; {@link #creativeEdit} puts the inserted items into
 * the ender chest, and a stand-in it took out becomes the real stack when it lands in a slot ({@link #creativeSlot}).
 */
public final class EnderPockets {
	private EnderPockets() {}

	/** {@link #dropFillBar}'s phase on {@link PolymerItemUtils#ITEM_MODIFICATION_EVENT}: after metacraft-bundles adds its bar. */
	public static final Identifier FILL_BAR_PHASE = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "ender_pockets");
	/** The client stack's custom data key for {@link #fingerprint} of the view it shows, read back by {@link #creativeEdit}. */
	static final String VIEW_KEY = "ovvar:ender_view";
	/** How deep {@link #forView} looks into containers in containers; anything deeper is shown empty. */
	private static final int MAX_DEPTH = 8;

	/** The view a client stack is being built with, while {@link #clientStack} runs on this thread; null otherwise. */
	private static final ThreadLocal<BundleContents> BUILDING = new ThreadLocal<>();

	/** Server thread only: the ender chest as last seen, for each player who has an Ender Pockets ovve to keep current. */
	private static final Map<UUID, List<ItemStack>> SEEN = new HashMap<>();
	/** Players whose preview read tore on the network thread ({@link #view}): resent at the end of the tick. */
	private static final Set<UUID> TORN = ConcurrentHashMap.newKeySet();

	public static void init() {
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, FILL_BAR_PHASE);
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register(FILL_BAR_PHASE, EnderPockets::dropFillBar);
		ServerTickEvents.END_SERVER_TICK.register(EnderPockets::tick);
	}

	public static boolean has(ItemStack ovve) {
		return OvveEnchantments.level(ovve, OvveEnchantments.ENDER_POCKETS) > 0;
	}

	// ---- the ovve's own pockets

	/**
	 * Throws out whatever is in an Ender Pockets ovve's own pockets (put there before it was enchanted):
	 * a player drops it, any other holder spawns it where it stands. The size factor stays (metacraft-bundles
	 * keeps it on {@code EMPTY}). Returns whether anything was thrown.
	 */
	public static boolean spill(ItemStack ovve, @Nullable Entity holder) {
		if (holder == null || !has(ovve)) return false;
		BundleContents contents = ovve.get(DataComponents.BUNDLE_CONTENTS);
		if (contents == null || contents.isEmpty() || !(holder.level() instanceof ServerLevel level)) return false;
		ovve.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
		contents.itemCopies().forEach(copy -> {
			if (copy.isEmpty()) return;
			if (holder instanceof Player player) player.drop(copy, false, Prediction.SERVER_ONLY);
			else holder.spawnAtLocation(level, copy);
		});
		return true;
	}

	// ---- the view

	/**
	 * What the viewer's client is shown as the pockets: one entry per non-empty slot of their ender chest, from 26
	 * down to 0, each a {@link #shownEntry stand-in} that weighs as little as a vanilla bundle lets an entry weigh,
	 * with {@code selected} applied the way the bundle applies its own selection. EMPTY without a viewer.
	 *
	 * <p>A vanilla client treats the pockets as a bundle: at a total weight of 1 it draws the bar full and red and,
	 * in creative (where its own inventory logic decides), refuses every insert before anything is sent. Shown as
	 * they are, one stack of 64 already weighs 1. As stand-ins of count 1 that stack up to {@value #SHOWN_MAX_STACK}
	 * a full chest of 27 weighs 27/{@value #SHOWN_MAX_STACK}: the bar is a sliver (4 pixels of 13, never red) and a
	 * stack of 64 can go in. A vanilla 26.3 bundle has no component that hides the bar while keeping the icons.
	 */
	public static BundleContents view(@Nullable ServerPlayer viewer, int selected) {
		if (viewer == null) return BundleContents.EMPTY;
		List<ItemStackTemplate> items = new ArrayList<>();
		try {
			Container ender = viewer.getEnderChestInventory();
			PacketContext context = viewer.connection == null ? PacketContext.get() : viewer.connection.getPacketContext();
			for (int slot = ender.getContainerSize() - 1; slot >= 0; slot--) {
				ItemStack stack = ender.getItem(slot);
				if (!stack.isEmpty()) items.add(shownEntry(stack, slot, context, viewer.registryAccess()));
			}
		} catch (RuntimeException e) {
			// The network thread builds client stacks while the server thread may be changing the chest: a torn
			// read shows nothing rather than failing the packet, and the slot is resent at the end of the tick.
			Ovvar.LOGGER.debug("[{}] ender pockets: reading {}'s ender chest: {}", Ovvar.MOD_ID, viewer.getUUID(), e.toString());
			TORN.add(viewer.getUUID());
			return BundleContents.EMPTY;
		}
		BundleContents view = new BundleContents(items);
		if (selected < 0) return view;
		BundleContents.Mutable mutable = view.asMutable();
		mutable.toggleSelectedItem(selected);
		return mutable.toImmutable();
	}

	/** The most a {@code max_stack_size} component may say (its codec's range): the lightest a bundle entry can be. */
	static final int SHOWN_MAX_STACK = 99;
	/** The custom data key on a {@link #shownEntry stand-in}: the ender slot it shows and {@link #identity} of what was there. */
	static final String SHOWN_KEY = "ovvar:ender_shown";

	/**
	 * One ender slot as the client's pockets show it, already in its client form: the stack's icon, model, glint and
	 * name (with its count, "Stone ×64", since the icon is drawn as one), but count 1, stackable to
	 * {@value #SHOWN_MAX_STACK} and without what a vanilla bundle weighs heavier (durability, nested bundle contents,
	 * bees), so the entry weighs 1/{@value #SHOWN_MAX_STACK}. Marked {@link #SHOWN_KEY} with its slot: a stand-in is
	 * never a real item, and one that comes back from a creative client is swapped for the real stack
	 * ({@link #creativeSlot}).
	 */
	private static ItemStackTemplate shownEntry(ItemStack real, int slot, @Nullable PacketContext context, HolderLookup.Provider lookup) {
		ItemStack client = PolymerItemUtils.getPolymerItemStack(forView(real, 0).copyWithCount(1), context, lookup).copyWithCount(1);
		if (real.getCount() > 1) {
			client.set(DataComponents.CUSTOM_NAME, Component.empty().withStyle(style -> style.withItalic(false))
					.append(client.getHoverName()).append(" ×" + real.getCount()));
		}
		client.remove(DataComponents.MAX_DAMAGE);
		client.remove(DataComponents.DAMAGE);
		client.remove(DataComponents.BUNDLE_CONTENTS);
		client.remove(DataComponents.BEES);
		client.set(DataComponents.MAX_STACK_SIZE, SHOWN_MAX_STACK);
		CustomData.update(DataComponents.CUSTOM_DATA, client, tag -> {
			// Polymer's own copy of the server stack would let it turn the stand-in back into a real item on the way in.
			tag.remove(PolymerItemUtils.POLYMER_STACK);
			CompoundTag shown = new CompoundTag();
			shown.putInt("slot", slot);
			shown.putInt("of", identity(real));
			tag.put(SHOWN_KEY, shown);
		});
		return ItemStackTemplate.fromNonEmptyStack(client);
	}

	/** What a stand-in says was in its slot: the whole stack, count and components. */
	private static int identity(ItemStack real) {
		return ItemStackTemplate.fromNonEmptyStack(real).hashCode();
	}

	/** A stand-in's {@link #SHOWN_KEY} (slot, identity), or null for anything else. */
	private static int @Nullable [] shownMark(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag shown = data.copyTag().getCompoundOrEmpty(SHOWN_KEY);
		if (shown.isEmpty()) return null;
		return new int[] {shown.getIntOr("slot", -1), shown.getIntOr("of", 0)};
	}

	private static boolean isShownEntry(ItemStackTemplate entry) {
		return shownMark(entry.create()) != null;
	}

	/** {@link #fingerprint} of the chest as it is now: the stand-ins {@link #view} would show, by their marks. */
	private static int fingerprintNow(Container ender) {
		List<Integer> marks = new ArrayList<>();
		for (int slot = ender.getContainerSize() - 1; slot >= 0; slot--) {
			ItemStack stack = ender.getItem(slot);
			if (!stack.isEmpty()) {
				marks.add(slot);
				marks.add(identity(stack));
			}
		}
		return marks.hashCode();
	}

	/**
	 * A stack as the preview shows it: an Ender Pockets ovve marked so its own preview is empty, and the
	 * contents of a shulker box, bundle or any other item container walked for the same, up to
	 * {@link #MAX_DEPTH}. Returns the stack itself when nothing needs changing.
	 */
	private static ItemStack forView(ItemStack stack, int depth) {
		if (has(stack)) {
			ItemStack copy = stack.copy();
			copy.set(ModComponents.ENDER_NESTED, Unit.INSTANCE);
			return copy;
		}
		ItemStack copy = null;
		for (TypedDataComponent<?> component : stack.getComponents()) {
			if (!(component.value() instanceof ContainerComponent<?> container) || container.size() == 0) continue;
			List<ItemStack> shown = new ArrayList<>();
			boolean changed = depth + 1 >= MAX_DEPTH;
			if (!changed) {
				for (ItemStack inside : container.itemCopies().toList()) {
					ItemStack s = inside.isEmpty() ? inside : forView(inside, depth + 1);
					changed |= s != inside;
					shown.add(s);
				}
			}
			if (!changed) continue;
			if (copy == null) copy = stack.copy();
			setUnchecked(copy, component.type(), container.copyWithContents(shown.stream()));
		}
		return copy == null ? stack : copy;
	}

	@SuppressWarnings("unchecked")
	private static <T> void setUnchecked(ItemStack stack, DataComponentType<T> type, Object value) {
		stack.set(type, (T) value);
	}

	/** The ender slot behind view index {@code viewIndex} (see {@link #view}), or −1. */
	public static int slotOf(ServerPlayer player, int viewIndex) {
		return slotIn(player.getEnderChestInventory(), viewIndex);
	}

	private static int slotIn(Container ender, int viewIndex) {
		if (viewIndex < 0) return -1;
		int seen = 0;
		for (int slot = ender.getContainerSize() - 1; slot >= 0; slot--) {
			if (!ender.getItem(slot).isEmpty() && seen++ == viewIndex) return slot;
		}
		return -1;
	}

	/** The bundle's selection, kept on the ovve since its contents are not its own: the same index again (or −1) clears it. */
	public static void toggleSelected(ItemStack ovve, int index) {
		int current = ovve.getOrDefault(ModComponents.ENDER_SELECTED, -1);
		if (index < 0 || index == current) ovve.remove(ModComponents.ENDER_SELECTED);
		else ovve.set(ModComponents.ENDER_SELECTED, index);
	}

	// ---- clicks (BundleItem's rules and return values, with the ender chest as the contents)

	/** The ovve on the cursor, clicked onto {@code slot}. */
	public static boolean stackedOnOther(ItemStack ovve, Slot slot, ClickAction action, Player player) {
		spill(ovve, player);
		ItemStack other = slot.getItem();
		if (action == ClickAction.PRIMARY && !other.isEmpty()) {
			boolean moved = takeFromSlot(player, slot);
			if (moved) playInsert(player);
			else playInsertFail(player);
			afterClick(player, slot);
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			Container ender = player.getEnderChestInventory();
			int from = takeOutSlot(ender, ovve);
			// A slot that refuses the stack (an armour slot, a result slot) leaves it where it is in the chest.
			if (from >= 0 && slot.mayPlace(ender.getItem(from))) {
				ItemStack removed = takeOut(player, ovve);
				ItemStack rest = slot.safeInsert(removed);
				if (rest.getCount() < removed.getCount()) playRemoveOne(player);
				putBack(player, rest);
			} else {
				ovve.remove(ModComponents.ENDER_SELECTED);
				playInsertFail(player);
			}
			afterClick(player, slot);
			return true;
		}
		return false;
	}

	/** {@code other} (the cursor) clicked onto the ovve in {@code slot}, worn or not. */
	public static boolean otherStackedOnMe(ItemStack ovve, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		spill(ovve, player);
		if (action == ClickAction.PRIMARY && other.isEmpty()) {
			toggleSelected(ovve, -1);
			return false;
		}
		if (action == ClickAction.PRIMARY) {
			boolean moved = false;
			if (slot.allowModification(player)) {
				ItemStack rest = player.getEnderChestInventory().addItem(other.copy());
				moved = rest.getCount() < other.getCount();
				other.setCount(rest.getCount());
			}
			if (moved) playInsert(player);
			else playInsertFail(player);
			afterClick(player, slot);
			return true;
		}
		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			if (slot.allowModification(player)) {
				ItemStack removed = takeOut(player, ovve);
				if (!removed.isEmpty()) {
					playRemoveOne(player);
					carried.set(removed);
				}
			}
			afterClick(player, slot);
			return true;
		}
		toggleSelected(ovve, -1);
		return false;
	}

	/** Moves as much of the slot's stack as the ender chest has room for into it. */
	private static boolean takeFromSlot(Player player, Slot slot) {
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		ItemStack other = slot.getItem();
		int room = room(ender, other);
		if (room <= 0) return false;
		ItemStack taken = slot.safeTake(other.getCount(), room, player);
		if (taken.isEmpty()) return false;
		ItemStack rest = ender.addItem(taken);
		if (!rest.isEmpty()) putBack(player, slot.safeInsert(rest));
		return true;
	}

	private static int room(Container ender, ItemStack stack) {
		int max = ender.getMaxStackSize(stack), room = 0;
		for (int slot = 0; slot < ender.getContainerSize(); slot++) {
			ItemStack there = ender.getItem(slot);
			if (there.isEmpty()) room += max;
			else if (ItemStack.isSameItemSameComponents(there, stack)) room += Math.max(0, max - there.getCount());
		}
		return room;
	}

	/**
	 * The ender slot {@link #takeOut} would empty: the selected one, or, with nothing selected or a selection
	 * that no longer maps to a stack, the newest. Never the ovve itself (it may sit in the ender chest, and is
	 * not something to take out of its own pockets): then the newest other stack. −1 if there is none.
	 */
	private static int takeOutSlot(Container ender, ItemStack ovve) {
		int slot = slotIn(ender, ovve.getOrDefault(ModComponents.ENDER_SELECTED, -1));
		if (slot >= 0 && ender.getItem(slot) != ovve) return slot;
		for (int i = ender.getContainerSize() - 1; i >= 0; i--) {
			ItemStack there = ender.getItem(i);
			if (!there.isEmpty() && there != ovve) return i;
		}
		return -1;
	}

	/** Takes the whole selected stack (see {@link #takeOutSlot}) out of the ender chest; clears the selection like the bundle does. */
	private static ItemStack takeOut(Player player, ItemStack ovve) {
		Container ender = player.getEnderChestInventory();
		int slot = takeOutSlot(ender, ovve);
		ovve.remove(ModComponents.ENDER_SELECTED);
		if (slot < 0) return ItemStack.EMPTY;
		ItemStack taken = ender.getItem(slot);
		ender.setItem(slot, ItemStack.EMPTY);
		return taken;
	}

	/** What did not fit where it was going: back into the ender chest, else the inventory, else the ground. */
	private static void putBack(Player player, ItemStack rest) {
		if (rest.isEmpty()) return;
		ItemStack left = player.getEnderChestInventory().addItem(rest);
		if (!left.isEmpty()) player.getInventory().placeItemBackInInventory(left, Prediction.SERVER_ONLY);
	}

	// Sounds go to the clicker only, as metacraft-bundles' BundleItemMixin sends them: the vanilla
	// playSound from a player skips that player, whose client did not predict this.
	private static void playInsert(Player player) {
		sound(player, SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
	}

	private static void playRemoveOne(Player player) {
		sound(player, SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
	}

	private static void playInsertFail(Player player) {
		sound(player, SoundEvents.BUNDLE_INSERT_FAIL, 1.0f, 1.0f);
	}

	private static void sound(Player player, SoundEvent sound, float volume, float pitch) {
		if (player instanceof ServerPlayer p) {
			p.connection.send(new ClientboundSoundEntityPacket(Holder.direct(sound), player.getSoundSource(), player, volume, pitch,
					player.level().getRandom().nextLong()));
		}
	}

	/**
	 * After a click, as BundleItem and metacraft-bundles' {@code updateIfClicked} do: tell the menu the
	 * inventory changed, then, at the end of the tick, resend the clicked slot and the cursor (the client
	 * predicted a bundle, not an ender chest).
	 */
	private static void afterClick(Player player, Slot slot) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != null) menu.slotsChanged(player.getInventory());
		if (!(player instanceof ServerPlayer p) || menu == null) return;
		TaskScheduler.scheduleImmediately(p.level().getServer(), () -> {
			if (p.containerMenu != menu) return;
			p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), slot.index, slot.getItem().copy()));
			p.connection.send(new ClientboundSetCursorItemPacket(menu.getCarried().copy()));
		});
	}

	// ---- keeping the preview current

	/**
	 * END_SERVER_TICK: for every player with an Ender Pockets ovve in an open menu or on the cursor, compares the
	 * ender chest with its copy from the last tick and, if it changed (a block's screen, the screen {@link #open}
	 * opens, a second ovve, a command) or a preview read tore, resends those slots. Players without one cost a
	 * scan of their menus' slots; nothing here throws.
	 */
	private static void tick(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (SEEN.size() > players.size() || TORN.size() > players.size()) {
			Set<UUID> online = new HashSet<>();
			for (ServerPlayer player : players) online.add(player.getUUID());
			SEEN.keySet().retainAll(online);
			TORN.retainAll(online);
		}
		for (ServerPlayer player : players) {
			try {
				refresh(player);
			} catch (RuntimeException e) {
				Ovvar.LOGGER.debug("[{}] ender pockets: keeping {}'s preview current: {}", Ovvar.MOD_ID, player.getUUID(), e.toString());
			}
		}
	}

	private static void refresh(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!carries(player)) {
			SEEN.remove(id);
			TORN.remove(id);
			return;
		}
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		List<ItemStack> seen = SEEN.get(id);
		boolean changed = seen != null && !same(seen, ender);
		if (seen == null || changed) SEEN.put(id, snapshot(ender));
		// The first time a player has one, its slot was just sent with the chest as it is.
		if (TORN.remove(id) || changed) resend(player);
	}

	private static boolean carries(ServerPlayer player) {
		AbstractContainerMenu open = player.containerMenu;
		if (anyIn(open)) return true;
		return open != player.inventoryMenu && anyIn(player.inventoryMenu);
	}

	private static boolean anyIn(AbstractContainerMenu menu) {
		if (isEnderOvve(menu.getCarried())) return true;
		for (Slot slot : menu.slots) if (isEnderOvve(slot.getItem())) return true;
		return false;
	}

	private static boolean isEnderOvve(ItemStack stack) {
		return stack.getItem() instanceof OvveItem && has(stack);
	}

	private static boolean same(List<ItemStack> seen, Container ender) {
		if (seen.size() != ender.getContainerSize()) return false;
		for (int i = 0; i < seen.size(); i++) if (!ItemStack.matches(seen.get(i), ender.getItem(i))) return false;
		return true;
	}

	private static List<ItemStack> snapshot(Container ender) {
		List<ItemStack> copy = new ArrayList<>(ender.getContainerSize());
		for (int i = 0; i < ender.getContainerSize(); i++) copy.add(ender.getItem(i).copy());
		return copy;
	}

	/**
	 * Makes the menus believe the client has nothing in each slot (and cursor) holding an Ender Pockets ovve,
	 * so their next {@code broadcastChanges} sends it with the chest as it is now, and sends the open menu's
	 * at once. The inventory menu behind an open screen goes when it is back in front (its armour and offhand
	 * slots; the rest the open menu has just sent). A selection made in the old view is cleared, since its
	 * index may now point at a different stack.
	 */
	private static void resend(ServerPlayer player) {
		forget(player.containerMenu);
		if (player.containerMenu != player.inventoryMenu) forget(player.inventoryMenu);
		player.containerMenu.broadcastChanges();
	}

	private static void forget(AbstractContainerMenu menu) {
		for (int i = 0; i < menu.slots.size(); i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (!isEnderOvve(stack)) continue;
			stack.remove(ModComponents.ENDER_SELECTED);
			menu.setRemoteSlot(i, ItemStack.EMPTY);
		}
		if (isEnderOvve(menu.getCarried())) {
			menu.getCarried().remove(ModComponents.ENDER_SELECTED);
			menu.setRemoteCarried(HashedStack.EMPTY);
		}
	}

	// ---- use

	/** Use: the ender chest screen, the same as the block's (EnderChestBlock). */
	public static InteractionResult open(ItemStack ovve, Player player) {
		spill(ovve, player);
		if (player instanceof ServerPlayer p) {
			p.getEnderChestInventory().setActiveChest(null);
			p.openMenu(new SimpleMenuProvider((id, inventory, opener) -> ChestMenu.threeRows(id, inventory, opener.getEnderChestInventory()),
					Component.translatable("container.enderchest")));
		}
		return InteractionResult.SUCCESS;
	}

	// ---- the client stack

	/**
	 * {@code OvveItem.getPolymerItemStack}'s part: builds the client stack with {@code build} and gives it the
	 * viewer's view as its pockets. While {@code build} runs, the fill bar ({@link #fillBar}) reads the view
	 * from {@link #BUILDING}, and an Ender Pockets ovve converted inside it gets an empty view.
	 */
	public static ItemStack clientStack(ItemStack ovve, @Nullable PacketContext context, Supplier<ItemStack> build) {
		BundleContents view = shownTo(ovve, context);
		BundleContents outer = BUILDING.get();
		BUILDING.set(view);
		ItemStack out;
		try {
			out = build.get();
		} finally {
			if (outer == null) BUILDING.remove();
			else BUILDING.set(outer);
		}
		out.set(DataComponents.BUNDLE_CONTENTS, view);
		if (!ovve.has(ModComponents.ENDER_NESTED) && outer == null && viewer(context) != null) {
			int shown = fingerprint(view.items());
			CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> tag.putInt(VIEW_KEY, shown));
		}
		return out;
	}

	private static BundleContents shownTo(ItemStack ovve, @Nullable PacketContext context) {
		if (ovve.has(ModComponents.ENDER_NESTED) || BUILDING.get() != null) return BundleContents.EMPTY;
		return view(viewer(context), ovve.getOrDefault(ModComponents.ENDER_SELECTED, -1));
	}

	/**
	 * The player a client stack is for, or null: by the login's profile, as OvveTop finds it, else by the
	 * connection's play listener (a connection that never logged in, like a game test's mock player, has no profile).
	 */
	private static @Nullable ServerPlayer viewer(@Nullable PacketContext context) {
		if (context == null) return null;
		MinecraftServer server = context.get(PacketContext.SERVER_INSTANCE);
		GameProfile profile = context.get(PacketContext.GAME_PROFILE);
		if (server != null && profile != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(profile.id());
			if (player != null) return player;
		}
		Connection connection = context.get(PacketContext.CONNECTION);
		return connection != null && connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener ? listener.player : null;
	}

	/**
	 * Takes metacraft-bundles' fill bar off an Ender Pockets ovve: it measures the ovve's own pockets, which
	 * stay empty, and the ender chest's fullness is not the bundle's. The bar vanilla draws on the slot comes from
	 * the weight of {@code bundle_contents} on the client and cannot be taken off without taking the icons with it;
	 * the stand-ins keep it a sliver ({@link #view}).
	 */
	private static ItemStack dropFillBar(ItemStack server, ItemStack client, PacketContext context) {
		if (!(server.getItem() instanceof OvveItem) || !has(server)) return client;
		Component own = BundleHelper.getOccupancyText(server);
		ItemLore lore = client.get(DataComponents.LORE);
		// No bar to take off: metacraft-bundles' rendering is off.
		if (own == null || lore == null || !lore.lines().contains(own)) return client;
		List<Component> lines = new ArrayList<>(lore.lines());
		lines.remove(own);
		client.set(DataComponents.LORE, new ItemLore(lines));
		return client;
	}

	// ---- creative

	/** What {@link #creativeEdit} compares: the shown stand-ins' marks (slot, identity), in order. Other entries are skipped. */
	static int fingerprint(List<ItemStackTemplate> shown) {
		List<Integer> marks = new ArrayList<>();
		for (ItemStackTemplate entry : shown) {
			int[] mark = shownMark(entry.create());
			if (mark == null) continue;
			marks.add(mark[0]);
			marks.add(mark[1]);
		}
		return marks.hashCode();
	}

	/**
	 * {@code OvveItem.getDecodedItemStack}'s part, on the network thread, for a client stack coming back (a
	 * creative player's inventory slot). The vanilla client edits the pockets itself in creative: an insert adds
	 * an entry of its own next to the stand-ins, a take-out takes a stand-in onto its cursor. Inserts go into the
	 * ender chest on the server thread ({@link #applyCreativeEdit}); a take-out changes nothing here, the stack
	 * leaves the chest when the stand-in lands in a slot ({@link #creativeSlot}). Polymer hands the server the
	 * ovve as it was, which is right: its own pockets stay empty.
	 */
	public static void creativeEdit(ItemStack client, @Nullable PacketContext context) {
		ServerPlayer player = viewer(context);
		CustomData data = client.get(DataComponents.CUSTOM_DATA);
		if (player == null || data == null) return;
		CompoundTag tag = data.copyTag();
		if (!tag.contains(VIEW_KEY)) return;
		int shown = tag.getIntOr(VIEW_KEY, 0);
		List<ItemStackTemplate> edited = List.copyOf(client.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items());
		MinecraftServer server = player.level().getServer();
		TaskScheduler.scheduleImmediately(server, () -> {
			try {
				applyCreativeEdit(player, shown, edited, context);
			} catch (RuntimeException e) {
				Ovvar.LOGGER.debug("[{}] ender pockets: {}'s creative edit: {}", Ovvar.MOD_ID, player.getUUID(), e.toString());
			}
		});
	}

	/**
	 * A creative client's edit of the pockets it was shown, applied to the ender chest: the entries it added
	 * (anything not a stand-in) go in, in their server form, and what does not fit is gone, as from a creative
	 * cursor. Only a creative player's, and only if the stand-ins it sent back are the ones it was shown and the
	 * chest still holds what they show (else the client had not seen the latest chest, or sent the same insert
	 * twice). The client is sent the chest as it is afterwards.
	 */
	static void applyCreativeEdit(ServerPlayer player, int shown, List<ItemStackTemplate> edited, @Nullable PacketContext context) {
		if (player.isRemoved() || !player.hasInfiniteMaterials()) return;
		TORN.add(player.getUUID());
		PlayerEnderChestContainer ender = player.getEnderChestInventory();
		if (fingerprint(edited) != shown || fingerprintNow(ender) != shown) return;
		for (ItemStackTemplate entry : edited) {
			if (isShownEntry(entry)) continue;
			ItemStack added = scrub(PolymerItemUtils.getRealItemStack(entry.create(), context, player.registryAccess()), 0);
			int max = Math.max(1, added.getMaxStackSize());
			for (int left = added.getCount(); left > 0 && !added.isEmpty(); left -= max) ender.addItem(added.copyWithCount(Math.min(left, max)));
		}
	}

	/**
	 * A creative slot packet's stack, before the server puts it in the slot ({@code EnderPocketsCreativeMixin}): a
	 * stand-in the client took out of the pockets becomes the real stack from the ender slot it shows, which
	 * leaves the chest now — if the slot still holds exactly that, else nothing (a stand-in is never a real item).
	 * Stand-ins inside a container are taken out of it. The client is then sent the slot as it really is.
	 */
	public static ItemStack creativeSlot(ServerPlayer player, int slotNum, ItemStack stack) {
		int[] mark = shownMark(stack);
		ItemStack out;
		if (mark != null) {
			PlayerEnderChestContainer ender = player.getEnderChestInventory();
			int slot = mark[0];
			ItemStack real = slot >= 0 && slot < ender.getContainerSize() ? ender.getItem(slot) : ItemStack.EMPTY;
			if (!real.isEmpty() && identity(real) == mark[1]) {
				out = real;
				ender.setItem(slot, ItemStack.EMPTY);
			} else {
				out = ItemStack.EMPTY;
			}
		} else {
			out = scrub(stack, 0);
			if (out == stack) return stack;
		}
		TORN.add(player.getUUID());
		AbstractContainerMenu menu = player.inventoryMenu;
		if (slotNum >= 0 && slotNum < menu.slots.size()) {
			TaskScheduler.scheduleImmediately(player.level().getServer(), () -> player.connection.send(
					new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), slotNum, menu.getSlot(slotNum).getItem().copy())));
		}
		return out;
	}

	/** {@code stack} without any stand-in in its containers (walked as {@link #forView} walks them); itself when there is none. */
	private static ItemStack scrub(ItemStack stack, int depth) {
		if (stack.isEmpty() || depth >= MAX_DEPTH) return stack;
		ItemStack copy = null;
		for (TypedDataComponent<?> component : stack.getComponents()) {
			if (!(component.value() instanceof ContainerComponent<?> container) || container.size() == 0) continue;
			List<ItemStack> kept = new ArrayList<>();
			boolean changed = false;
			for (ItemStack inside : container.itemCopies().toList()) {
				if (!inside.isEmpty() && shownMark(inside) != null) {
					changed = true;
					kept.add(ItemStack.EMPTY);
					continue;
				}
				ItemStack s = scrub(inside, depth + 1);
				changed |= s != inside;
				kept.add(s);
			}
			if (!changed) continue;
			if (copy == null) copy = stack.copy();
			setUnchecked(copy, component.type(), container.copyWithContents(kept.stream()));
		}
		return copy == null ? stack : copy;
	}
}

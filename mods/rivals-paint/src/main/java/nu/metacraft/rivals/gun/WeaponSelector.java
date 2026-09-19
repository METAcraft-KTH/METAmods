package nu.metacraft.rivals.gun;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import nu.metacraft.rivals.Rivals;

import java.util.List;

/**
 * The weapon selector: a Polymer item the client is shown as a compass, which opens {@link WeaponDialog}
 * when it is clicked in the inventory screen — where it lives, in {@link #SLOT}, the top-right slot of the
 * main grid.
 *
 * <p>An item rather than only a command, because a player who has just joined a lobby has not read the
 * commands and a thing sitting in their inventory asks to be clicked. A compass because it already reads as
 * "point me at something". Polymer sends the item's own id as the client's {@code item_model}, so the pack ships
 * {@code items/weapon_selector.json}: a still compass face (vanilla's {@code compass_16}), because a
 * compass that points somewhere spins its needle while it works out where — without that file the
 * client drew the missing-texture square.
 *
 * <p>The name is on the stack ({@link DataComponents#ITEM_NAME}) rather than left to the lang file: what
 * reaches the client is a compass, and a compass is called Compass unless the stack says otherwise.
 */
public final class WeaponSelector extends Item implements PolymerItem {
	public static final Identifier ID = Rivals.id("weapon_selector");
	public static final Component NAME = Component.literal("Weapon selector").withStyle(ChatFormatting.AQUA);

	/**
	 * Where the selector lives: the top-right slot of the main inventory grid. A player's inventory is the
	 * hotbar in 0..8 and the three-row grid in 9..35 ({@code Inventory.isHotbarSlot}, {@code INVENTORY_SIZE}
	 * 36), so 17 is the right-hand end of the grid's first row — the corner of the screen, out of the way of
	 * everything and in the same place every round.
	 *
	 * <p>Out of the hotbar on purpose. It was in it, which cost a hotbar slot and, worse, made the hotbar
	 * lock two slots wide: the selection could sit on the selector, so a player could end up in a firefight
	 * holding a compass. The hotbar now has exactly one place to be — the weapon — and the selector is
	 * clicked where it sits, in the inventory screen.
	 */
	public static final int SLOT = 17;

	private static WeaponSelector item;

	private WeaponSelector(Properties properties) {
		super(properties);
	}

	public static void register() {
		item = Registry.register(BuiltInRegistries.ITEM, ID, new WeaponSelector(
				new Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, ID))));
	}

	/** The registered item; null until {@link #register()} has run. */
	public static WeaponSelector get() {
		return item;
	}

	/** One selector, named. What the lobby hands out. */
	public static ItemStack stack() {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, NAME);
		return stack;
	}

	public static boolean is(ItemStack stack) {
		return stack.getItem() instanceof WeaponSelector;
	}

	/**
	 * Put this player's selector back where it belongs: nothing to do if it is already in {@link #SLOT} or
	 * if they are carrying none, otherwise the one they have is moved there — from wherever a sweep, an
	 * arm-up or an inventory shuffle left it. Whatever was in the slot is moved aside by
	 * {@link WeaponPicks#intoSlot}. Returns whether anything moved.
	 *
	 * <p>Move, never create. This used to be find-or-create, which made it the thing that resurrected the
	 * selector the whistle had just taken back: the lobby called it every time a round ended and every time
	 * anybody joined. Handing one out is {@link #give}, which only {@link nu.metacraft.rivals.Match#arm}
	 * calls — so a selector exists exactly while its owner is in a match.
	 */
	public static boolean home(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		if (is(inventory.getItem(SLOT))) return false;
		ItemStack selector = strayOf(player);
		if (selector.isEmpty()) return false;
		WeaponPicks.intoSlot(player, SLOT, selector);
		return true;
	}

	/**
	 * The same, and a selector for whoever has none: the arm-up's form, and the only way one is ever handed
	 * out. Find-or-create rather than add-if-missing, so there is never a second selector to lose track of.
	 * Returns whether anything moved.
	 */
	public static boolean give(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		if (is(inventory.getItem(SLOT))) return false;
		ItemStack stray = strayOf(player);
		WeaponPicks.intoSlot(player, SLOT, stray.isEmpty() ? stack() : stray);
		return true;
	}

	/**
	 * Every selector off this player, wherever it is sitting, and how many went. What the whistle and the
	 * lobby take back: between matches nobody carries one, so there is nothing left to click and nothing to
	 * take home in a bag of compasses.
	 */
	public static int take(Player player) {
		Inventory inventory = player.getInventory();
		int taken = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (is(inventory.getItem(slot))) {
				inventory.setItem(slot, ItemStack.EMPTY);
				taken++;
			}
		}
		return taken;
	}

	/** The selector this player is carrying somewhere other than its slot, taken out of it; empty if none. */
	private static ItemStack strayOf(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (is(inventory.getItem(slot))) {
				ItemStack selector = inventory.getItem(slot);
				inventory.setItem(slot, ItemStack.EMPTY);
				return selector;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Does this player already carry one? What keeps the lobby from handing out a second. */
	public static boolean carried(Player player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (is(player.getInventory().getItem(slot))) return true;
		}
		return false;
	}

	/**
	 * How many times the picker has been asked for, ever. There is no screen to read on a mock player — a
	 * dialog is one packet at a connection that swallows it — so this is the seam a test can see the second
	 * way in through, the way {@code /rivals weapons} is a command a test can call.
	 */
	private static int opens;

	public static int opens() {
		return opens;
	}

	/**
	 * Open the picker for this player, from wherever they asked. Counted, and the hand is put back on the
	 * weapon — an inventory click does not move the selection, but a right click from the hotbar does, and
	 * either way the player should be holding their gun the moment they have picked one. Nothing to put back
	 * for anyone who is not carrying a picked weapon.
	 */
	public static void openPicker(ServerPlayer player) {
		opens++;
		WeaponDialog.open(player);
		if (WeaponLock.locked(player)) WeaponLock.pin(player);
	}

	/**
	 * The same, for a click on the selector <em>inside</em> the inventory screen, which is where it is: the
	 * selector is out of the hotbar, so this is the way in, and {@link WeaponLock} routes the click here
	 * rather than letting it pick the compass up.
	 *
	 * <p>The screen has to go first. A dialog is drawn over whatever screen the client has open, so a picker
	 * opened behind the inventory would be a picker nobody can see — hence {@code closeContainer}, and the
	 * show-dialog packet immediately after it, in that order on the wire.
	 */
	public static void openFromInventory(ServerPlayer player) {
		player.closeContainer();
		openPicker(player);
	}

	/**
	 * Right click with one in hand. Not a path a player on a normal round can take — the selector is not in
	 * the hotbar and the hotbar is locked to the weapon — but an operator who has handed themselves things
	 * with {@code /rivals gun} can be holding anything, and a selector that did nothing in the hand would
	 * read as broken.
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
		openPicker(serverPlayer);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.COMPASS;
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		tooltip.add(Component.literal("Click me in your inventory to pick your weapon").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context,
			net.minecraft.core.HolderLookup.Provider lookup) {
		ItemStack client = PolymerItem.super.getPolymerItemStack(stack, flag, context, lookup);
		// A compass points at a lodestone or at spawn and its needle spins while it works that out; this one
		// is a button, so tell the client it is tracking nothing at all.
		client.remove(DataComponents.LODESTONE_TRACKER);
		client.set(DataComponents.ITEM_NAME, NAME);
		return client;
	}
}

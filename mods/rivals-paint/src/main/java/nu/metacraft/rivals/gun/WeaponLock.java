package nu.metacraft.rivals.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Once you have picked a weapon, it is the weapon you are carrying and it is in the slot it was put in.
 *
 * <p>A round of Rivals is four weapons to choose between and one of them in your hands, and everything
 * about the hotbar was working against that: a player could scroll off the gun onto an empty slot and
 * stand in a firefight punching, drop the gun and have no way of getting it back, or drag it out of the
 * inventory screen into the void. So the pick is a commitment — {@link WeaponPicks#GIVEN_SLOT} holds the
 * weapon, the hotbar selection is pinned there and nowhere else, the stack cannot be dropped and it cannot
 * be moved — and <em>swapping</em> is the one thing that stays open, through the {@link WeaponSelector},
 * which sits in the inventory grid rather than the hotbar and is clicked where it sits.
 *
 * <p>Everything here is a plain question about an inventory, so the packet handlers that ask it
 * ({@link nu.metacraft.rivals.mixin.ServerGamePacketListenerImplMixin}) stay three lines each and the
 * rules are testable without a client. The lock is on <em>carrying a picked weapon</em> rather than on
 * the match state: a player who has a gun in its slot has one whatever the clock says, and that includes
 * an operator who handed themselves one with {@code /rivals gun}.
 */
public final class WeaponLock {
	private WeaponLock() {}

	/** Is there a picked weapon in its slot? Everything below is a no-op when there is not. */
	public static boolean locked(Player player) {
		return player.getInventory().getItem(WeaponPicks.GIVEN_SLOT).getItem() instanceof PaintWeapon;
	}

	/**
	 * May this player put the hotbar selection on this slot? While locked, the weapon's own slot and nothing
	 * else. There used to be a second allowed slot — whichever held the weapon selector — and the selector
	 * has since moved out of the hotbar entirely ({@link WeaponSelector#SLOT}), which is what made one
	 * allowed slot possible: a locked hotbar with two places to be is a hotbar a player can end up holding a
	 * compass in during a firefight.
	 */
	public static boolean maySelect(Player player, int slot) {
		return !locked(player) || slot == WeaponPicks.GIVEN_SLOT;
	}

	/**
	 * Put the selection back on the weapon and tell the client so. A vanilla client has already moved its
	 * own selection by the time the packet arrives — it does not wait to be told — so the server's
	 * {@code setSelectedSlot} alone would leave the two disagreeing about which item is in the hand;
	 * {@link ClientboundSetHeldSlotPacket} is what snaps the client back.
	 */
	public static void pin(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		inventory.setSelectedSlot(WeaponPicks.GIVEN_SLOT);
		if (player.connection != null) player.connection.send(new ClientboundSetHeldSlotPacket(WeaponPicks.GIVEN_SLOT));
	}

	/**
	 * The answer to a set-carried-item packet: whether it is refused, and the snap back with it. Said out
	 * loud once — a selection that springs back with no explanation reads as lag — and only when it was a
	 * real attempt to leave, which is every refusal, since the allowed slots are refused by nobody.
	 */
	public static boolean refuseSlot(ServerPlayer player, int slot) {
		if (slot < 0 || slot >= Inventory.getSelectionSize() || maySelect(player, slot)) return false;
		pin(player);
		say(player, "Your weapon is locked in its slot — click the selector in your inventory to swap it");
		return true;
	}

	/**
	 * Whoever is carrying a picked weapon may not throw it — nor the selector, which is the only way back
	 * to a different weapon and would otherwise be droppable into a lake. Both drop actions ask this:
	 * {@code DROP_ITEM} for one and {@code DROP_ALL_ITEMS} for the stack, and both drop what is in the
	 * hand, which the pin keeps to those two things.
	 */
	public static boolean refuseDrop(ServerPlayer player) {
		if (!locked(player)) return false;
		ItemStack held = player.getInventory().getSelectedItem();
		if (!(held.getItem() instanceof PaintWeapon) && !WeaponSelector.is(held)) return false;
		say(player, WeaponSelector.is(held)
				? "The selector is how you swap weapons — it stays with you"
				: "You cannot drop your weapon");
		return true;
	}

	/**
	 * The answer to a container click: whether it is refused, and the resync with it. A refused click is
	 * simply not run, so the server's inventory never changes — but the client has already drawn the drag
	 * or the swap it predicted, so the menu is sent again whole rather than left showing a move that did
	 * not happen.
	 *
	 * <p>A click on the weapon <em>selector</em> is cancelled for a different reason: it is the picker, so
	 * the screen is closed and the dialog opened instead of the click being run.
	 *
	 * <p>Three ways a click can reach the weapon, and all three are refused: the clicked slot is the
	 * weapon's own (or holds a paint weapon, which is the same thing seen from the other side, and covers
	 * a gun an operator gave themselves into a backpack slot), the hotbar-swap key aimed at the weapon's
	 * slot from anywhere on the screen, or a paint weapon already on the cursor — which only a click that
	 * slipped through could have put there, and which must not be allowed to land anywhere new.
	 */
	public static boolean refuseContainerClick(ServerPlayer player, int slotNum, int button, ContainerInput input) {
		// The selector is the exception: a click on it is not a refusal but the picker. Answered whatever the
		// click was — every button and every drag on it means the same thing — and whether or not the player
		// is carrying a weapon yet, because a lobby player's selector lives in this screen too.
		if (isSelector(player, slotNum) || WeaponSelector.is(player.containerMenu.getCarried())) {
			player.containerMenu.sendAllDataToRemote();
			WeaponSelector.openFromInventory(player);
			return true;
		}
		if (!locked(player)) return false;
		if (!touches(player, slotNum, button, input)) return false;
		player.containerMenu.sendAllDataToRemote();
		say(player, "Your weapon stays in its slot — click the selector to swap it");
		return true;
	}

	/** Is the clicked slot the one the weapon selector is sitting in? */
	private static boolean isSelector(ServerPlayer player, int slotNum) {
		if (slotNum < 0 || slotNum >= player.containerMenu.slots.size()) return false;
		return WeaponSelector.is(player.containerMenu.slots.get(slotNum).getItem());
	}

	private static boolean touches(ServerPlayer player, int slotNum, int button, ContainerInput input) {
		if (player.containerMenu.getCarried().getItem() instanceof PaintWeapon) return true;
		if (input == ContainerInput.SWAP && button == WeaponPicks.GIVEN_SLOT) return true;
		if (slotNum < 0 || slotNum >= player.containerMenu.slots.size()) return false;
		Slot slot = player.containerMenu.slots.get(slotNum);
		return (slot.container == player.getInventory() && slot.getContainerSlot() == WeaponPicks.GIVEN_SLOT)
				|| slot.getItem().getItem() instanceof PaintWeapon;
	}

	private static void say(ServerPlayer player, String what) {
		if (player.connection != null) {
			player.sendSystemMessage(Component.literal(what).withStyle(ChatFormatting.GRAY), true);
		}
	}
}

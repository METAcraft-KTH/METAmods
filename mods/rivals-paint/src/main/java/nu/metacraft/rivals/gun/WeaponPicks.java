package nu.metacraft.rivals.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Taking a weapon: what a pick does to an inventory, and the line that describes each weapon.
 *
 * <p>No screen of its own — {@link WeaponDialog} is the screen, and it reaches this through
 * {@code /rivals weapons pick <id>}. Keeping the two apart is what let the picker stop being a chest
 * menu without touching what picking means: the sweep, the slot, the remembered choice.
 */
public final class WeaponPicks {
	/** Where the picked weapon goes: the first hotbar slot, so it is in hand a keypress later. */
	public static final int GIVEN_SLOT = 0;

	private WeaponPicks() {}

	/**
	 * The one line under a weapon's picture: what it is for, how far it reaches and what it costs, off the
	 * README's weapon table. Ink comes from the live tuning rather than the enum's default, so a server
	 * that has retuned a weapon describes the weapon its players are actually holding.
	 */
	public static String blurb(Weapon weapon) {
		WeaponTuning tuning = WeaponTuning.get(weapon);
		int ink = tuning.intValue(WeaponTuning.Param.INK);
		return switch (weapon) {
			case SHOOTER -> "Rapid fire, mid range — " + ink + " ink a shot, one every "
					+ tuning.intValue(WeaponTuning.Param.COOLDOWN) + " ticks";
			case CHARGER -> "Sniper, a charged line up to " + Math.round(tuning.value(WeaponTuning.Param.RANGE_FULL))
					+ " blocks — " + tuning.intValue(WeaponTuning.Param.CHARGE_INK_MIN) + " to "
					+ (tuning.intValue(WeaponTuning.Param.CHARGE_INK_MIN) + tuning.intValue(WeaponTuning.Param.CHARGE_INK_FULL)) + " ink";
			case SLOSHER -> "A lobbed bucketful, short range and a wide splat — " + ink + " ink a throw";
			case ROLLER -> "Ground cover at touching range — " + ink + " ink a flick, 1 every "
					+ tuning.intValue(WeaponTuning.Param.ROLL_INK_EVERY) + " ticks rolling";
		};
	}

	/**
	 * Take the weapon: every paint weapon out of the inventory, this one into the first slot, the pick
	 * remembered. Returns the stack that was given, so a test can read it.
	 *
	 * <p>Sweeping first matters — the point of picking is to be holding one weapon, not to be holding a
	 * fourth — and the sweep is {@link #sweep}, which the lobby uses too.
	 */
	public static ItemStack pick(ServerPlayer player, Weapon weapon) {
		WeaponChoice.of(player.level().getServer()).set(player, weapon);
		sweep(player);
		ItemStack given = PaintWeapon.withTankColor(new ItemStack(PaintWeapon.of(weapon)), player.getTeam());
		intoItsSlot(player, given);
		WeaponSelector.home(player);
		// The picker was opened from the inventory screen, which does not move the hand: put it on the gun.
		WeaponLock.pin(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7f, 1.2f);
		player.sendSystemMessage(Component.literal("You picked the " + weapon.displayName + ". "
				+ blurb(weapon)).withStyle(ChatFormatting.AQUA));
		// The other half of a loadout, asked once: somebody who has never picked a special is shown the
		// three now, while they are already in a picking frame of mind. Somebody who has picked is left
		// alone — being asked the same question every time you change guns is a nag, not a choice, and
		// the weapon picker's own last button is there for whoever wants to change it.
		if (SpecialChoice.of(player.level().getServer()).get(player).isEmpty()) SpecialDialog.open(player);
		return given;
	}

	/**
	 * The weapon into {@link #GIVEN_SLOT}, and whatever was in that slot somewhere else. The slot is the
	 * one the hotbar is locked to ({@link WeaponLock}), so a weapon that landed anywhere else would be a
	 * weapon its owner could never select — which is what {@code inventory.add} did whenever the slot was
	 * occupied.
	 */
	public static void intoItsSlot(ServerPlayer player, ItemStack given) {
		intoSlot(player, GIVEN_SLOT, given);
	}

	/**
	 * A stack into an exact slot, and whatever was in that slot somewhere sensible: home if it is the weapon
	 * selector ({@link WeaponSelector#SLOT}, its own corner of the inventory), the first free slot otherwise,
	 * and the floor only if there is nowhere at all — moved rather than destroyed, because losing the
	 * selector is losing the way to another weapon.
	 */
	public static void intoSlot(ServerPlayer player, int slot, ItemStack stack) {
		Inventory inventory = player.getInventory();
		ItemStack was = inventory.getItem(slot);
		inventory.setItem(slot, stack);
		if (was.isEmpty()) return;
		// Guarded on the slot as well as on the item, so sending a selector home can never be what displaced
		// it: one hop, never a loop.
		if (WeaponSelector.is(was) && slot != WeaponSelector.SLOT) {
			intoSlot(player, WeaponSelector.SLOT, was);
			return;
		}
		if (!inventory.add(was)) player.drop(was, false, Prediction.SERVER_ONLY);
	}

	/**
	 * Every paint weapon out of a player's inventory. Used by the picker (so a pick is a swap rather than
	 * a collection) and by the lobby, where nobody carries a gun. Returns how many stacks went.
	 */
	public static int sweep(Player player) {
		Inventory inventory = player.getInventory();
		int taken = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).getItem() instanceof PaintWeapon) {
				inventory.setItem(slot, ItemStack.EMPTY);
				taken++;
			}
		}
		return taken;
	}
}

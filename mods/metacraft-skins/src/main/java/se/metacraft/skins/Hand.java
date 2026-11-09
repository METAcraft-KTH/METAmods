package se.metacraft.skins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Handing a stack to a player, the one way {@code /itemskins give}, Extract and the rest do it: into
 * their inventory, and what doesn't fit dropped at their feet for them alone, with no pickup delay,
 * as vanilla's {@code /give} drops what doesn't fit. Server thread only.
 */
public final class Hand {
	private Hand() {}

	/** Gives the player {@code stack} (which it may use up); returns whether any of it had to be dropped. */
	public static boolean give(ServerPlayer player, ItemStack stack) {
		boolean added = player.getInventory().add(stack);
		if (added && stack.isEmpty()) return false;
		ItemEntity drop = player.createItemStackToDrop(stack, false, false);
		if (drop == null) return false;
		drop.setNoPickUpDelay();
		drop.setTarget(player.getUUID());
		drop.level().addFreshEntity(drop);
		return true;
	}
}

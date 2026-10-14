package nu.metacraft.nether_rifts;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** While rift mode is on, eyes of ender don't craft, so nobody stockpiles them for the End. */
public final class EyeLock {

	private EyeLock() {}

	public static ItemStack filter(ItemStack result, ServerLevel level) {
		if (result.is(Items.ENDER_EYE) && RiftWorldState.get(level.getServer()).riftMode()) {
			return ItemStack.EMPTY;
		}
		return result;
	}
}

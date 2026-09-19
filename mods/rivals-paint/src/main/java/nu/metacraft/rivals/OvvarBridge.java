package nu.metacraft.rivals;

import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The half of {@link Ovves} that names ovvar's classes. Never loaded unless ovvar is — see there.
 *
 * <p>Which chapters count as a side, best first: DATA is the Data ovve; IT is the silicon-blue IT ovve
 * ({@link Chapter#IT_KISEL}, which counts as IT) and then the plain IT one. A player wearing or owning the
 * better one keeps it; a player with none of them is given the first plain one, owned by them so the
 * wardrobe treats it as theirs.
 */
final class OvvarBridge {
	private OvvarBridge() {}

	static List<Chapter> chaptersOf(PaintColor color) {
		return switch (color) {
			case DATA -> List.of(Chapter.DATA);
			case IT -> List.of(Chapter.IT_KISEL, Chapter.IT);
		};
	}

	/** Is this stack one of the side's ovves, and not somebody else's? */
	private static boolean fits(ItemStack stack, List<Chapter> chapters, UUID wearer) {
		if (!(stack.getItem() instanceof OvveItem ovve) || !chapters.contains(ovve.chapter)) return false;
		UUID owner = OvveItem.owner(stack);
		return owner == null || owner.equals(wearer);
	}

	static boolean dress(ServerPlayer player, PaintColor color) {
		List<Chapter> chapters = chaptersOf(color);
		ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
		// Already right: the best chapter they could be wearing, or the only one there is for the side.
		if (fits(legs, chapters, player.getUUID())
				&& (chapters.size() == 1 || ((OvveItem) legs.getItem()).chapter == chapters.get(0))) {
			return false;
		}
		Inventory inventory = player.getInventory();
		ItemStack wear = ItemStack.EMPTY;
		int from = -1;
		// One of theirs out of the inventory, the better chapter first, then the legs themselves if what
		// is on them fits at all (a plain IT ovve on a player who owns no silicon-blue one).
		for (Chapter chapter : chapters) {
			for (int slot = 0; slot < inventory.getContainerSize() && wear.isEmpty(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.getItem() instanceof OvveItem ovve && ovve.chapter == chapter
						&& fits(stack, chapters, player.getUUID()) && stack != legs) {
					wear = stack;
					from = slot;
				}
			}
			if (!wear.isEmpty()) break;
		}
		if (wear.isEmpty() && fits(legs, chapters, player.getUUID())) return false;
		if (wear.isEmpty()) {
			wear = new ItemStack(ModContent.ovve(chapters.get(chapters.size() - 1)));
			OvveItem.setOwner(wear, player.getUUID());
		} else {
			inventory.setItem(from, ItemStack.EMPTY);
		}
		player.setItemSlot(EquipmentSlot.LEGS, wear);
		// Whatever was on the legs — the other side's ovve from a team change, ordinary trousers — is
		// theirs to keep, just not to wear in a round.
		if (!legs.isEmpty() && !inventory.add(legs)) player.drop(legs, false, Prediction.SERVER_ONLY);
		return true;
	}
}

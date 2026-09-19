package nu.metacraft.rivals;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Which team the ovve you are wearing puts you on.
 *
 * <p>Metacraft's ovvar mod dresses players in chapter overalls, and the arena should read the same way
 * the campus does: a DATA ovve is a DATA player. This is a soft integration — matched on the item's
 * registry id, so there is no compile or runtime dependency on ovvar and an arena without it behaves
 * exactly as before. Ids are {@code ovvar:data_ovve}, {@code ovvar:it_ovve}, their {@code _top} and
 * {@code _feet} variants and the chapter-specific ones ({@code it_kisel_ovve},
 * {@code it_polymiter_ovve}, {@code data_polymiter_ovve}); anything else in the namespace, notably
 * {@code ovvar:media_frack}, belongs to no chapter here and leaves the player's team alone.
 *
 * <p>The mapping is on the id's <em>prefix</em> rather than on a list of known ids, so an ovve ovvar
 * adds next term dresses a player for the right team without a change here.
 */
public final class OvveTeams {
	/** The namespace an ovve's registry id lives in. */
	public static final String NAMESPACE = "ovvar";
	/** The slots an ovve occupies, in the order they are read: trousers first, then the top. */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.LEGS, EquipmentSlot.CHEST};

	private OvveTeams() {}

	/** The chapter an ovvar item id belongs to, or empty for anything that is not a chapter ovve. */
	public static Optional<PaintColor> colourOf(Identifier itemId) {
		if (!NAMESPACE.equals(itemId.getNamespace())) return Optional.empty();
		String path = itemId.getPath();
		for (PaintColor color : PaintColor.values()) {
			if (path.startsWith(color.id)) return Optional.of(color);
		}
		return Optional.empty();
	}

	/** The same for a worn stack; an empty stack, or one from any other mod, is empty. */
	public static Optional<PaintColor> colourOf(ItemStack worn) {
		if (worn.isEmpty()) return Optional.empty();
		return colourOf(BuiltInRegistries.ITEM.getKey(worn.getItem()));
	}

	/**
	 * The chapter the player is dressed as, from the first ovve they are wearing. Trousers before top,
	 * because the trousers are the half of an ovve that is always there — the {@code _top} is optional.
	 */
	public static Optional<PaintColor> worn(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			Optional<PaintColor> color = colourOf(player.getItemBySlot(slot));
			if (color.isPresent()) return color;
		}
		return Optional.empty();
	}
}

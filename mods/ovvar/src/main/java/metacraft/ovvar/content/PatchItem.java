package metacraft.ovvar.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import metacraft.ovvar.store.Stash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** A patch in the hand: a stackable item whose icon is its sewn-on art, shown to clients as paper. */
public final class PatchItem extends Item implements PolymerItem {
	public final Patches.Patch patch;
	private final Identifier id;

	public PatchItem(Properties properties, Patches.Patch patch, Identifier id) {
		super(properties);
		this.patch = patch;
		this.id = id;
	}

	/**
	 * The other models of every patch: a piece of its art 1:1 on the 16×16 sprite, for display
	 * entities ({@link ModComponents#FLAT}), plain or ghosted (the preview of a patch not sewn yet).
	 */
	public static String flatModel(String itemName, Patches.Art art, PatchPieces.Piece piece, boolean ghost) {
		return itemName + "_flat_" + flatKey(art, piece, ghost);
	}

	/**
	 * What {@link ModComponents#FLAT} carries for a piece: which of the patch's PNGs it is cut from
	 * ({@link Patches#artFor} — a shoulder shows a different one than a chest cell) and the piece's
	 * own corners within it.
	 */
	public static String flatKey(Patches.Art art, PatchPieces.Piece piece, boolean ghost) {
		return (ghost ? "ghost_" : "") + (art.byDefault() ? "" : art.width() + "x" + art.height() + "_") + piece.key();
	}

	/** In a player's inventory on a server that banks patches (a minigame server): into their stash, at once. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (entity instanceof ServerPlayer player) Stash.bank(player, stack);
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		tooltip.add(Component.literal(patch.seat() ? "Goes across the seat" : "Goes anywhere on an ovve").withStyle(ChatFormatting.GRAY));
		if (patch.artist() != null) tooltip.add(Component.literal("Art by " + patch.artist()).withStyle(ChatFormatting.GRAY));
		if (stack.has(ModComponents.SESSION)) {
			tooltip.add(Component.literal("From your stash: aim at your ovve on the stand, right-click to sew").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			tooltip.add(Component.literal("Aim it at an armour stand wearing an ovve, right-click to sew").withStyle(ChatFormatting.DARK_GRAY));
			tooltip.add(Component.literal("Or keep it safe: /ovvar stash").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.PAPER;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		String piece = stack.get(ModComponents.FLAT);
		return piece == null ? id : id.withSuffix("_flat_" + piece);
	}
}

package metacraft.ovvar.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.store.DesignStoreConfig;
import metacraft.ovvar.store.Wardrobe;
import metacraft.ovvar.store.Wardrobes;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A chapter's ovve: one item, worn in the legs slot, with pockets. It is a bundle with
 * {@link Pockets#SIZE} times the room (through metacraft-bundles, which also shows the real fill
 * level to vanilla clients) that can also be filled while worn, by clicking items onto the legs slot.
 *
 * Right click behaves as a bundle (hold to empty it); sneak + right click rolls the top up or
 * down. Neither equips it — drag it into the slot or shift-click. While the top is up the mod
 * keeps a companion {@link OvveTopItem} in the chest slot so the sleeves render. Leather-grade
 * defence, no durability (it breaking would spill someone's pockets). The client is handed a
 * bundle with our equipment asset, chosen per stack.
 *
 * An ovve belongs to a player ({@link ModComponents#OWNER}, set when a player first holds it, and
 * moved to a later holder only under {@code designs.others_ovve: rebind}, see {@link Ownership}) and
 * its patches are that player's design in their wardrobe ({@link Wardrobes}); the component on the
 * item is a copy kept in step every tick, so a second ovve of the same owner looks the same and
 * never holds a patch of its own.
 */
public final class OvveItem extends BundleItem implements PolymerItem {
	public final Chapter chapter;
	private final Identifier id;

	public OvveItem(Properties properties, Chapter chapter, Identifier id) {
		super(properties);
		this.chapter = chapter;
		this.id = id;
	}

	public static boolean topUp(ItemStack ovve) {
		return Boolean.TRUE.equals(ovve.get(ModComponents.TOP_UP));
	}

	public static void setTopUp(ItemStack ovve, boolean up) {
		if (up) ovve.set(ModComponents.TOP_UP, true);
		else ovve.remove(ModComponents.TOP_UP);
	}

	// ---- ownership

	public static @Nullable UUID owner(ItemStack ovve) {
		return ovve.get(ModComponents.OWNER);
	}

	public static void setOwner(ItemStack ovve, @Nullable UUID owner) {
		if (owner == null) ovve.remove(ModComponents.OWNER);
		else ovve.set(ModComponents.OWNER, owner);
	}

	/**
	 * Copies the owner's cached design onto the ovve when it differs (the store is the truth; the
	 * item only draws). Nothing happens for an unowned ovve, one whose owner is not loaded, or one
	 * whose owner has no wardrobe at all yet (their first ovve's patches are adopted by their tick).
	 */
	public static void refresh(ItemStack ovve) {
		UUID owner = owner(ovve);
		if (owner == null || !(ovve.getItem() instanceof OvveItem item) || !Wardrobes.loaded(owner)) return;
		Wardrobe wardrobe = Wardrobes.current(owner);
		if (wardrobe.version() == 0 && !Wardrobes.pending(owner)) return;
		Optional<SpotPlacements> design = wardrobe.design(item.chapter);
		if (!design.equals(Looks.sewn(ovve))) Looks.setSewn(ovve, design.orElse(null));
	}

	/**
	 * The store side of a player's inventory tick: bind an unowned ovve to this player, adopt the
	 * patches on it as their first design if they have no wardrobe at all, and keep the copy on
	 * the item in step with the design. {@code bind_on_pickup} is what lets an ovve change hands at
	 * all, so with it off nothing binds and nothing rebinds either, whatever {@code others_ovve} says.
	 */
	public static void syncDesign(ServerPlayer player, ItemStack stack) {
		if (owner(stack) == null) {
			if (!OvvarConfig.get().designs().bindOnPickup()) return;
			setOwner(stack, player.getUUID());
		} else if (!player.getUUID().equals(owner(stack))
				&& OvvarConfig.get().designs().othersOvve() == DesignStoreConfig.OthersOvve.REBIND
				&& OvvarConfig.get().designs().bindOnPickup()) {
			// A given ovve becomes the new holder's: their design, not the giver's (designs.others_ovve).
			setOwner(stack, player.getUUID());
		}
		UUID owner = owner(stack);
		if (owner == null || !(stack.getItem() instanceof OvveItem item)) return;
		if (!Wardrobes.loaded(owner)) {
			Wardrobes.fetch(owner);
			return;
		}
		Wardrobe wardrobe = Wardrobes.current(owner);
		if (wardrobe.version() == 0 && owner.equals(player.getUUID()) && !Wardrobes.pending(owner)) {
			// A first ovve with patches already on it (given by command, sewn while unowned): they become the design.
			var sewn = Looks.sewn(stack);
			if (sewn.isEmpty()) return;
			Wardrobes.update(owner, w -> w.version() == 0 ? w.withDesign(item.chapter, sewn.get()) : null, outcome -> {
				// A conflict or rejection here is a give that wrote the same design a moment earlier; the refetch settles it.
				if (outcome != Wardrobes.Outcome.OK) Ovvar.LOGGER.debug("[ovvar] wardrobes: adopting {}'s first design: {}", owner, outcome);
			});
			return;
		}
		refresh(stack);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown()) {
			var bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundleContents == null || bundleContents.isEmpty()) {
				Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
				if (equippable != null && equippable.swappable()) {
					// Somebody else's ovve never swaps into the slot (the armour slot refuses it too).
					if (player instanceof ServerPlayer serverPlayer) {
						String refusal = Ownership.wearRefusal(serverPlayer, stack);
						if (refusal != null) {
							Ownership.refuse(serverPlayer, refusal);
							return InteractionResult.FAIL;
						}
					} else if (Ownership.blocksWearing(player, stack)) {
						return InteractionResult.FAIL;
					}
					return equippable.swapWithEquipmentSlot(stack, player);
				}
			}
			return super.use(level, player, hand);
		}
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		if (!chapter.rollable) {
			serverPlayer.sendOverlayMessage(Component.literal("A " + chapter.garmentWord() + " has nothing to roll down"));
			return InteractionResult.FAIL;
		}
		boolean up = !topUp(stack);
		setTopUp(stack, up);
		serverPlayer.sendOverlayMessage(Component.literal(up ? "Top rolled up" : "Top rolled down"));
		return InteractionResult.SUCCESS;
	}

	// ---- wearing

	/** Worn in the legs slot (player or armour stand): keep the companion top in step every tick. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (entity instanceof ServerPlayer player) {
			// Off a stand and into a player's hands: the armour draws the patches again, and if
			// this player's pack cannot show them all, this is the one reload a sewing session ends in.
			if (stack.has(ModComponents.ON_STAND)) stack.remove(ModComponents.ON_STAND);
			syncDesign(player, stack);
			// The backstop under the equip checks: an ovve that got into the slot anyway (/item replace,
			// a mod, a rule changed while it was worn) comes off again, into the inventory it came from.
			if (slot == EquipmentSlot.LEGS && Ownership.blocksWearing(player, stack)) {
				evict(player, stack);
				return;
			}
			Looks.claimIfNeeded(player, stack);
		} else {
			refresh(stack);   // on a stand or a mannequin: follow the owner's design (loaded on demand)
			UUID owner = owner(stack);
			if (owner != null && !Wardrobes.loaded(owner)) Wardrobes.fetch(owner);
		}
		if (slot == EquipmentSlot.LEGS && entity instanceof LivingEntity wearer) {
			OvveTop.sync(wearer, stack);
			OvveFeet.sync(wearer, stack);
		}
	}

	/** Takes a foreign ovve off this player, back into their inventory (or onto the ground), with a word in chat. */
	private static void evict(ServerPlayer player, ItemStack ovve) {
		String refusal = Ownership.wearRefusal(player, ovve);
		ItemStack taken = ovve.copy();
		player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		if (!player.getInventory().add(taken)) player.drop(taken, false, Prediction.SERVER_ONLY);
		if (refusal != null) Ownership.refuse(player, refusal);
	}

	/** The tooltip's patch list wraps at about this many columns — enough for a name and a count. */
	public static final int PATCH_LIST_WIDTH = 40;
	/**
	 * The most lines the patch list may take. With the status line above it and the hint line
	 * below, that keeps the whole tooltip to 5 lines under the name — no taller than an item with
	 * five enchantments, however many patches are sewn on.
	 */
	public static final int PATCH_LIST_LINES = 3;

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		boolean up = topUp(stack);
		List<Placement> sewn = SpotPlacements.asPlacementList(Looks.sewn(stack));
		tooltip.add(Component.literal("Zipped " + (up ? "up" : "down") + " · " + countLabel(sewn.size())).withStyle(ChatFormatting.GRAY));
		for (String line : patchLines(sewn, PATCH_LIST_WIDTH, PATCH_LIST_LINES)) {
			tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		StringBuilder hint = new StringBuilder();
		if (chapter.rollable) hint.append("Sneak + use: zip ").append(up ? "down" : "up").append(" · ");
		hint.append("Use: empty pockets");
		tooltip.add(Component.literal(hint.toString()).withStyle(ChatFormatting.DARK_GRAY));
		if (sewn.isEmpty()) {
			// Kept as its own short line rather than folded into the hint above: with no patches the
			// list above is empty, so the tooltip is still only 3 lines tall.
			tooltip.add(Component.literal("Sew patches on at an armour stand").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static String countLabel(int count) {
		if (count == 0) return "no patches yet";
		return count + (count == 1 ? " patch" : " patches");
	}

	/**
	 * The tooltip's patch list, at most {@code maxLines} long whatever is sewn on: patches grouped
	 * by name in sewn order (a repeat becomes "name ×N" rather than a line of its own — spot labels
	 * are dropped too, since the wardrobe screen and the stand already show where things are), then
	 * greedy-wrapped at {@code width} columns without ever splitting a name across lines. What still
	 * does not fit shrinks the last line to make room for "… +N more", N counting patches, not
	 * names, left out. Pure and player-free, so a game test can hammer it without a stand.
	 */
	public static List<String> patchLines(List<Placement> sewn, int width, int maxLines) {
		if (sewn.isEmpty() || maxLines <= 0) return List.of();
		LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
		for (Placement p : sewn) counts.merge(p.patch().name(), 1, Integer::sum);
		List<String> tokens = new ArrayList<>();
		List<Integer> tokenCounts = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			tokens.add(entry.getValue() > 1 ? entry.getKey() + " ×" + entry.getValue() : entry.getKey());
			tokenCounts.add(entry.getValue());
		}

		List<Integer> starts = new ArrayList<>();
		List<String> full = new ArrayList<>();
		int[] end = new int[1];
		for (int i = 0; i < tokens.size(); i = end[0]) {
			starts.add(i);
			full.add(greedyLine(tokens, i, width, end));
		}
		if (full.size() <= maxLines) return full;

		List<String> lines = new ArrayList<>(full.subList(0, maxLines - 1));
		int j = starts.get(maxLines - 1);
		StringBuilder last = new StringBuilder();
		while (j < tokens.size()) {
			String candidate = last.isEmpty() ? tokens.get(j) : last + ", " + tokens.get(j);
			int remaining = sum(tokenCounts, j + 1, tokens.size());
			String withSuffix = remaining > 0 ? candidate + ", … +" + remaining + " more" : candidate;
			if (withSuffix.length() > width) break;
			last = new StringBuilder(candidate);
			j++;
		}
		if (j < tokens.size()) {
			String suffix = "… +" + sum(tokenCounts, j, tokens.size()) + " more";
			last = new StringBuilder(last.isEmpty() ? suffix : last + ", " + suffix);
		}
		if (!last.isEmpty()) lines.add(last.toString());
		return lines;
	}

	/** One greedy-wrapped line from {@code start}, never splitting a token; {@code endOut[0]} is where the next line picks up. */
	private static String greedyLine(List<String> tokens, int start, int width, int[] endOut) {
		StringBuilder sb = new StringBuilder();
		int i = start;
		while (i < tokens.size()) {
			String candidate = sb.isEmpty() ? tokens.get(i) : sb + ", " + tokens.get(i);
			if (!sb.isEmpty() && candidate.length() > width) break;
			sb = new StringBuilder(candidate);
			i++;
		}
		endOut[0] = i;
		return sb.toString();
	}

	private static int sum(List<Integer> counts, int from, int to) {
		int total = 0;
		for (int i = from; i < to; i++) total += counts.get(i);
		return total;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.BUNDLE;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return id;
	}

	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context, HolderLookup.Provider lookup) {
		ItemStack out = PolymerItem.super.getPolymerItemStack(stack, flag, context, lookup);
		OvveTop.dress(out, stack.get(DataComponents.EQUIPPABLE), stack, chapter, Piece.BOTTOM, !topUp(stack), context, lookup);
		return out;
	}
}

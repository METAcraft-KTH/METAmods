package se.metacraft.ovvar.content.enchantment;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;
import se.metacraft.bundles.util.BundleHelper;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.mixin.KeyringDiggingAccessor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyring: when a survival player starts breaking a block, the best tool in the worn garment's pockets
 * comes to the selected hotbar slot, and the hand's item goes into the pocket the tool came from. The
 * tool goes back when no break action has come for {@code swap_back_ticks}, right after a break that
 * leaves it below {@link #DURABILITY_FLOOR}, and on leaving. A held mouse button sends nothing between
 * START and STOP, so a player the server still sees digging counts as acting ({@link #digging}).
 *
 * Items only ever move between the slot and the pockets; nothing is created or deleted. Whenever the
 * slot or the pockets no longer look the way the swap left them (the tool moved, broke or was dropped,
 * the garment came off, the player died), the swap is simply forgotten and every item stays where it is.
 * The state is in memory only: if the server stops mid-swap, the tool is in the slot and the hand's item
 * in the pockets, both real.
 */
public final class Keyring {
	private Keyring() {}

	/** A pocket tool with less than this share of its durability left is never picked, and goes back after a break. Edvin's number. */
	public static final float DURABILITY_FLOOR = 0.02f;

	/**
	 * A swap in progress.
	 *
	 * @param inventorySlot the hotbar slot the tool went to
	 * @param tool          the tool's item
	 * @param original      what the slot held (EMPTY if nothing), now in the pockets
	 * @param pocketIndex   the pocket entry the tool came from, where {@code original} went
	 * @param lastAction    the server tick of the last break action
	 */
	public record Swap(int inventorySlot, Item tool, ItemStack original, int pocketIndex, long lastAction) {
		Swap touched(long now) {
			return new Swap(inventorySlot, tool, original, pocketIndex, now);
		}
	}

	/** Touched from entity ticks too, which async (parallel entity ticking) runs off the server thread. */
	private static final Map<UUID, Swap> SWAPS = new ConcurrentHashMap<>();

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) afterBreak(serverPlayer);
		});
		ServerTickEvents.END_SERVER_TICK.register(Keyring::tick);
		// Leaving: KeyringLeaveMixin, on PlayerList.remove before the save (Fabric's DISCONNECT can fire on the netty thread).
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) SWAPS.remove(player.getUUID());
		});
	}

	public static @Nullable Swap swapOf(ServerPlayer player) {
		return SWAPS.get(player.getUUID());
	}

	// ---- the rules

	/** Is enough durability left (or none to lose)? */
	public static boolean usable(ItemStack tool) {
		return !tool.isDamageableItem() || (tool.getMaxDamage() - tool.getDamageValue()) >= DURABILITY_FLOOR * tool.getMaxDamage();
	}

	/**
	 * The pocket index of the tool to swap in for breaking {@code state}, or −1. Only usable items that are
	 * correct for drops or faster than a bare hand count; they rank by (correct for drops, destroy speed),
	 * ties to the lower index, and the best must beat the hand on the same ranking. The caller skips
	 * blocks with zero destroy time.
	 */
	public static int pick(List<ItemStack> pockets, ItemStack hand, BlockState state) {
		int best = -1;
		boolean bestCorrect = hand.isCorrectToolForDrops(state);
		float bestSpeed = hand.getDestroySpeed(state);
		for (int i = 0; i < pockets.size(); i++) {
			ItemStack stack = pockets.get(i);
			if (stack.isEmpty() || !usable(stack)) continue;
			boolean correct = stack.isCorrectToolForDrops(state);
			float speed = stack.getDestroySpeed(state);
			if (!correct && speed <= 1) continue;
			if (correct && !bestCorrect || correct == bestCorrect && speed > bestSpeed) {
				best = i;
				bestCorrect = correct;
				bestSpeed = speed;
			}
		}
		return best;
	}

	// ---- the hooks

	/** Every break action, before vanilla reads the held tool ({@code KeyringBreakActionMixin}). */
	public static void beforeBreakAction(ServerPlayer player, BlockPos pos, ServerboundPlayerActionPacket.Action action) {
		long now = player.level().getServer().getTickCount();
		Swap swap = SWAPS.get(player.getUUID());
		if (swap != null) SWAPS.put(player.getUUID(), swap.touched(now));
		// ABORT never swaps back: the vanilla client sends ABORT then START when its held item changes.
		if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return;
		ServerLevel level = player.level();
		// The game mode the break is handled in (ServerPlayerGameMode's own), as vanilla reads it below.
		GameType mode = player.gameMode.getGameModeForPlayer();
		if (mode != GameType.SURVIVAL || player.blockActionRestricted(level, pos, mode)) return;
		if (!player.isWithinBlockInteractionRange(pos, 1.0) || !level.mayInteract(player, pos)
				|| level.getServer().isUnderSpawnProtection(level, pos, player)) return;
		ItemStack garment = OvveItem.worn(player);
		if (OvveEnchantments.level(garment, OvveEnchantments.KEYRING) <= 0 || EnderPockets.has(garment)) return;
		BlockState state = level.getBlockState(pos);
		if (state.getDestroySpeed(level, pos) <= 0) return;

		Inventory inventory = player.getInventory();
		if (swap != null) {
			boolean same = swap.inventorySlot() == inventory.getSelectedSlot() && inventory.getSelectedItem().is(swap.tool());
			// The tool out is kept unless a pocket (the original item included) now beats it.
			if (same && pick(pocketsOf(garment), inventory.getSelectedItem(), state) < 0) return;
			swapBack(player);
		}
		ItemStack hand = inventory.getSelectedItem();
		if (!hand.isEmpty() && !BundleContents.canItemBeInBundle(hand)) return;
		List<ItemStack> pockets = pocketsOf(garment);
		int index = pick(pockets, hand, state);
		if (index < 0) return;

		ItemStack tool = pockets.get(index);
		List<ItemStack> after = new ArrayList<>(pockets);
		if (hand.isEmpty()) after.remove(index);
		else after.set(index, hand.copy());
		BundleContents contents = withItems(garment, after);
		if (contents == null) return;
		garment.set(DataComponents.BUNDLE_CONTENTS, contents);
		inventory.setItem(inventory.getSelectedSlot(), tool);
		SWAPS.put(player.getUUID(), new Swap(inventory.getSelectedSlot(), tool.getItem(), hand.copy(), index, now));
	}

	/** {@code PlayerBlockBreakEvents.AFTER}: a tool worn below the floor goes back at once. */
	public static void afterBreak(ServerPlayer player) {
		Swap swap = SWAPS.get(player.getUUID());
		if (swap == null) return;
		ItemStack inSlot = player.getInventory().getItem(swap.inventorySlot());
		if (!inSlot.is(swap.tool()) || !usable(inSlot)) swapBack(player);
	}

	public static void tick(MinecraftServer server) {
		tick(server, OvvarConfig.get().enchantments().keyring().swapBackTicks());
	}

	/**
	 * END_SERVER_TICK: the tool goes back after {@code swapBackTicks} with no break action and no dig in progress,
	 * and at once when the swap is no longer intact (it is then just forgotten); a dead or gone player's swap is
	 * forgotten.
	 */
	public static void tick(MinecraftServer server, int swapBackTicks) {
		if (SWAPS.isEmpty()) return;
		long now = server.getTickCount();
		List<ServerPlayer> due = new ArrayList<>();
		for (Iterator<Map.Entry<UUID, Swap>> it = SWAPS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Swap> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || player.isRemoved() || player.isDeadOrDying()) {
				it.remove();
			} else if (!intact(player, entry.getValue())) {
				due.add(player);
			} else if (digging(player)) {
				entry.setValue(entry.getValue().touched(now));
			} else if (now - entry.getValue().lastAction() >= swapBackTicks) {
				due.add(player);
			}
		}
		for (ServerPlayer player : due) swapBack(player);
	}

	/** Is the swap still as it was left: the tool in its slot, worn on a Keyring garment? Only then does a dig keep it going. */
	private static boolean intact(ServerPlayer player, Swap swap) {
		Inventory inventory = player.getInventory();
		if (swap.inventorySlot() < 0 || swap.inventorySlot() >= inventory.getContainerSize()) return false;
		ItemStack garment = OvveItem.worn(player);
		return inventory.getItem(swap.inventorySlot()).is(swap.tool())
				&& OvveEnchantments.level(garment, OvveEnchantments.KEYRING) > 0 && !EnderPockets.has(garment);
	}

	/** Is the server still breaking a block for this player (between START and STOP, or a delayed destroy)? */
	public static boolean digging(ServerPlayer player) {
		KeyringDiggingAccessor mode = (KeyringDiggingAccessor) player.gameMode;
		return mode.isOvvarDestroyingBlock() || mode.isOvvarDelayedDestroy();
	}

	/**
	 * Puts the tool back into the pocket entry that holds the original item, and the original back into the
	 * slot, if both are still where the swap left them and the pockets have room. Either way the swap is
	 * over. Nothing is created or deleted: on any mismatch the items simply stay where they are.
	 */
	public static void swapBack(ServerPlayer player) {
		Swap swap = SWAPS.remove(player.getUUID());
		if (swap == null || player.isRemoved() || player.isDeadOrDying()) return;
		Inventory inventory = player.getInventory();
		if (swap.inventorySlot() < 0 || swap.inventorySlot() >= inventory.getContainerSize()) return;
		ItemStack tool = inventory.getItem(swap.inventorySlot());
		if (tool.isEmpty() || !tool.is(swap.tool())) return;
		// The tool goes only into a garment that still has Keyring, as intact checks: anything else keeps it.
		ItemStack garment = OvveItem.worn(player);
		if (OvveEnchantments.level(garment, OvveEnchantments.KEYRING) <= 0 || EnderPockets.has(garment)) return;
		List<ItemStack> pockets = pocketsOf(garment);
		List<ItemStack> after = new ArrayList<>(pockets);
		ItemStack original;
		if (swap.original().isEmpty()) {
			original = ItemStack.EMPTY;
			after.add(Math.clamp(swap.pocketIndex(), 0, after.size()), tool);
		} else {
			int at = entryOf(pockets, swap.original(), swap.pocketIndex());
			if (at < 0) return;
			original = pockets.get(at);
			after.set(at, tool);
		}
		BundleContents contents = withItems(garment, after);
		if (contents == null) return;
		garment.set(DataComponents.BUNDLE_CONTENTS, contents);
		inventory.setItem(swap.inventorySlot(), original);
	}

	// ---- pockets

	private static List<ItemStack> pocketsOf(ItemStack garment) {
		BundleContents contents = garment.get(DataComponents.BUNDLE_CONTENTS);
		return contents == null ? List.of() : contents.itemCopies().toList();
	}

	/**
	 * The pocket entry holding the original item: the same item and components, the exact stack at its old
	 * index first, then the exact stack anywhere, then any count of it (it may have been topped up). −1 if gone.
	 */
	private static int entryOf(List<ItemStack> pockets, ItemStack original, int hint) {
		if (hint >= 0 && hint < pockets.size() && ItemStack.matches(pockets.get(hint), original)) return hint;
		for (int i = 0; i < pockets.size(); i++) if (ItemStack.matches(pockets.get(i), original)) return i;
		for (int i = 0; i < pockets.size(); i++) if (ItemStack.isSameItemSameComponents(pockets.get(i), original)) return i;
		return -1;
	}

	/** The garment's contents with exactly these entries, in this order, at its size factor; null if they would not fit. */
	private static @Nullable BundleContents withItems(ItemStack garment, List<ItemStack> items) {
		List<ItemStackTemplate> templates = new ArrayList<>(items.size());
		for (ItemStack stack : items) if (!stack.isEmpty()) templates.add(ItemStackTemplate.fromNonEmptyStack(stack));
		BundleContents contents = BundleHelper.fixBundle(new BundleContents(templates), garment);
		Fraction weight = contents.weight().result().orElse(null);
		return weight == null || weight.compareTo(Fraction.ONE) > 0 ? null : contents;
	}
}

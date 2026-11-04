package se.metacraft.skins.ui;

import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.gui.SimpleGui;
import se.metacraft.skins.Skins;
import se.metacraft.skins.item.SkinBox;
import se.metacraft.skins.item.SkinToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A right-click with a Skin Token or a Skin Box in the main hand, from the use-item, use-block and
 * use-entity events: it opens the token's claim menu or the box's menu, and does nothing else.
 * Vanilla's own action (opening a chest, filling an item frame, placing the vault a client thinks
 * the box is, the offhand item's use) is cancelled, the arm swings, and one click opens one menu
 * however many packets it arrives as. Anything else is left to vanilla. Server thread only.
 */
public final class RightClick {
	private RightClick() {}

	/**
	 * One click can arrive as two packets (use-on-block or interact, then use-item), and a held
	 * button repeats every 4 ticks: no new menu opens this soon after one opened or closed.
	 */
	private static final int QUIET_TICKS = 10;
	/** The server tick each player's last menu opened or closed, or last click was refused. */
	private static final Map<ServerPlayer, Integer> LAST = new WeakHashMap<>();

	public static InteractionResult handle(ServerPlayer player, Skins skins, InteractionHand hand) {
		ItemStack held = player.getMainHandItem();
		boolean token = held.is(SkinToken.ITEM), box = held.is(SkinBox.ITEM);
		if (!token && !box || player.isSpectator()) return InteractionResult.PASS;
		if (hand != InteractionHand.MAIN_HAND || isOpen(player) || recently(player)) return InteractionResult.FAIL;
		quiet(player);
		player.swing(InteractionHand.MAIN_HAND, held.getInteractAnimation(), true);
		if (token) ClaimMenu.open(player, skins, held);
		else openBox(player, skins, held);
		return InteractionResult.CONSUME;
	}

	/** A box whose file is Dormant, gone or never was is refused; any other opens nothing yet. */
	private static void openBox(ServerPlayer player, Skins skins, ItemStack box) {
		boolean openable = SkinBox.boxOf(box).flatMap(skins::box).filter(b -> !b.dormant()).isPresent();
		if (!openable) Feedback.failed(player, "This Skin Box can't be opened right now.");
	}

	private static boolean isOpen(ServerPlayer player) {
		return player.containerMenu instanceof SlotBasedWrapperMenu menu && menu.getBackingGui() instanceof Menu;
	}

	private static boolean recently(ServerPlayer player) {
		Integer last = LAST.get(player);
		return last != null && player.level().getServer().getTickCount() - last < QUIET_TICKS;
	}

	/** Starts the quiet time now. */
	private static void quiet(ServerPlayer player) {
		LAST.put(player, player.level().getServer().getTickCount());
	}

	/**
	 * A 3-row chest a right-click opens, with the player's own inventory locked; closing it starts
	 * the quiet time, so a still-held button doesn't reopen it at once.
	 */
	abstract static class Menu extends SimpleGui {
		Menu(ServerPlayer player) {
			super(MenuType.GENERIC_9x3, player, false);
			setLockPlayerInventory(true);
		}

		@Override
		public void onManualClose() {
			quiet(getPlayer());
		}

		@Override
		public void onPlayerClose(boolean success) {
			quiet(getPlayer());
		}
	}
}

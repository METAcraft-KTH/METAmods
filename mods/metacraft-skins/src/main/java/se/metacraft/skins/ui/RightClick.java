package se.metacraft.skins.ui;

import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.gui.SimpleGui;
import se.metacraft.skins.MetacraftSkins;
import se.metacraft.skins.Skins;
import se.metacraft.skins.item.SkinBox;
import se.metacraft.skins.item.SkinToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A right-click with a Skin Token or a Skin Box in the main hand, from the item's own {@code use} and
 * {@code useOn}: it opens the token's claim menu or the box's menu, with a swing. A block or an entity
 * clicked with one has its say first, as with any item (a chest opens, an item frame takes it; sneak
 * to skip the block). One click opens one menu however many packets it arrives as. Server thread only.
 */
public final class RightClick {
	private RightClick() {}

	/**
	 * One click can arrive as two packets (use-on-block, then use-item), and a held button repeats
	 * every 4 ticks: no new menu opens this soon after one opened or closed.
	 */
	private static final int QUIET_TICKS = 10;
	/** The server tick each player's last menu opened or closed, or last click was refused. */
	private static final Map<ServerPlayer, Integer> LAST = new WeakHashMap<>();

	/** Opens the menu of the token or box {@code player} holds in their main hand; for the item classes. */
	public static InteractionResult open(@Nullable Player player, InteractionHand hand) {
		Skins skins = MetacraftSkins.skins();
		if (!(player instanceof ServerPlayer server) || skins == null || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (isOpen(server) || recently(server)) return InteractionResult.FAIL;
		quiet(server);
		ItemStack held = server.getMainHandItem();
		server.swing(InteractionHand.MAIN_HAND, held.getInteractAnimation(), true);
		if (held.is(SkinToken.ITEM)) ClaimMenu.open(server, skins, held);
		else RollMenu.open(server, skins, SkinBox.boxOf(held).orElse(""));
		return InteractionResult.CONSUME;
	}

	/**
	 * Refuses the offhand's use, on air, a block or an entity, while the main hand holds a token or
	 * a box. The client thinks it holds paper or a vault, which do nothing, so it goes on to use the
	 * offhand in the same click: a pearl would be thrown as the menu opens.
	 */
	public static InteractionResult guardOffhand(Player player, InteractionHand hand) {
		ItemStack main = player.getMainHandItem();
		return hand == InteractionHand.OFF_HAND && (main.is(SkinToken.ITEM) || main.is(SkinBox.ITEM)) ? InteractionResult.FAIL : InteractionResult.PASS;
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

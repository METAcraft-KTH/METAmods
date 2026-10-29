package se.metacraft.skins.ui;

import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import se.metacraft.skins.Fit;
import se.metacraft.skins.OwnershipChange;
import se.metacraft.skins.PerkLines;
import se.metacraft.skins.SkinFolder;
import se.metacraft.skins.SkinSummary;
import se.metacraft.skins.item.SkinToken;
import se.metacraft.skins.Skins;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The Claim: right-clicking a Skin Token in the main hand (on air, a block or an entity) opens
 * "Claim &lt;Skin&gt;?", a 3-row chest built with sgui. Its middle row holds two green claim panes, the
 * skin with its Look and Kind, and two red cancel panes; everything else is filler. For a skin the
 * player already owns the claim panes are gray, the title is "Already owned", and the token stays
 * theirs to trade. Only ever calls the {@link Skins} module; the player's own inventory is locked.
 */
public final class ClaimMenu {
	private ClaimMenu() {}

	static final int[] CLAIM = {10, 11}, CANCEL = {15, 16};
	static final int SKIN = 13;
	/**
	 * One click can arrive as two packets (use-on-block or interact, then use-item), and a held
	 * button repeats every 4 ticks: no new Claim starts this soon after one opened or closed.
	 */
	private static final int QUIET_TICKS = 10;
	/** The server tick each player's last claim menu opened or closed (server thread only). */
	private static final Map<ServerPlayer, Integer> LAST = new WeakHashMap<>();
	private static final Feedback.Audience CLAIMING = new Feedback.Audience.Owner("claim", "you keep the token");

	/**
	 * A right-click, from the use-item, use-block and use-entity events. With a token in the main
	 * hand it's a Claim and nothing else: vanilla's own action (opening a chest, filling an item
	 * frame, the offhand item's use) is cancelled, the arm swings, a page turns, and the claim
	 * menu opens, once per click however many packets it arrives as. Anything else is left to vanilla.
	 */
	public static InteractionResult rightClick(ServerPlayer player, Skins skins, InteractionHand hand) {
		ItemStack token = player.getMainHandItem();
		if (!token.is(SkinToken.ITEM) || player.isSpectator()) return InteractionResult.PASS;
		if (hand != InteractionHand.MAIN_HAND || isOpen(player) || recently(player)) return InteractionResult.FAIL;
		LAST.put(player, player.level().getServer().getTickCount());
		player.swing(InteractionHand.MAIN_HAND, token.getInteractAnimation(), true);
		open(player, skins, token);
		return InteractionResult.CONSUME;
	}

	private static boolean isOpen(ServerPlayer player) {
		return player.containerMenu instanceof SlotBasedWrapperMenu menu && menu.getBackingGui() instanceof Gui;
	}

	private static boolean recently(ServerPlayer player) {
		Integer last = LAST.get(player);
		return last != null && player.level().getServer().getTickCount() - last < QUIET_TICKS;
	}

	/** Opens the claim menu for this token, the very stack a Claim then uses one of; a token whose skin is unknown or Dormant is refused instead. */
	public static void open(ServerPlayer player, Skins skins, ItemStack token) {
		String id = SkinToken.skinOf(token).orElse("");
		Optional<SkinSummary> found = skins.skin(id).filter(s -> !s.dormant() && s.kind().isPresent());
		if (found.isEmpty()) {
			Feedback.failed(player, "This token's skin (" + id + ") isn't on this server, so it can't be claimed.");
			return;
		}
		SkinSummary skin = found.get();
		String name = skin.displayName();
		boolean owned = skins.ownedSkins(player.getUUID()).owns(id);

		Gui gui = new Gui(player);
		gui.setTitle(Component.literal(owned ? "Already owned" : "Claim " + name + "?"));
		for (int slot = 0; slot < gui.getSize(); slot++) gui.setSlot(slot, filler());
		for (int slot : CLAIM) {
			if (owned) {
				gui.setSlot(slot, pane(Items.STAINED_GLASS_PANE.gray(), Component.literal("You already own " + name).withStyle(ChatFormatting.GRAY),
						"Keep the token to trade it", () -> Feedback.failed(player, Feedback.whyNot(OwnershipChange.ALREADY_OWNED, name, CLAIMING))));
			} else {
				gui.setSlot(slot, pane(Items.STAINED_GLASS_PANE.lime(), Component.literal("✔ Claim " + name).withStyle(ChatFormatting.GREEN),
						"The token is used up", () -> claim(player, skins, gui, token, name)));
			}
		}
		for (int slot : CANCEL) {
			gui.setSlot(slot, pane(Items.STAINED_GLASS_PANE.red(), Component.literal("✖ Cancel").withStyle(ChatFormatting.RED), "You keep the token", () -> {
				gui.close();
				Feedback.cancelled(player);
			}));
		}
		gui.setSlot(SKIN, icon(skin, skin.fit().orElseThrow(), owned));
		gui.open();
		Feedback.pageTurn(player);
	}

	private static void claim(ServerPlayer player, Skins skins, Gui gui, ItemStack token, String name) {
		OwnershipChange result = skins.claim(player, token);
		// Owned since the menu opened: it stays open, and only says so.
		if (result != OwnershipChange.ALREADY_OWNED) gui.close();
		if (result != OwnershipChange.DONE) {
			Feedback.failed(player, Feedback.whyNot(result, name, CLAIMING));
			return;
		}
		Feedback.claimed(player, name);
		SkinToken.resend(player);   // their other tokens for it now say it's owned
	}

	/** The skin in the centre: its Look on an item it fits, what it fits, and what claiming means. */
	private static GuiElement icon(SkinSummary skin, Fit fit, boolean owned) {
		GuiElementBuilder icon = GuiElementBuilder.from(new ItemStack(fit.icon()))
				.model(Identifier.fromNamespaceAndPath(SkinFolder.NAMESPACE, skin.id()))
				.setName(skin.styledName())
				.hideDefaultTooltip();
		fit.lore(skin.dyeable()).forEach(icon::addLoreLine);
		skin.release().ifPresent(r -> icon.addLoreLine(r.line()));
		PerkLines.of(skin.perks()).forEach(icon::addLoreLine);
		skin.description().ifPresent(d -> icon.addLoreLine(Component.literal(d).withStyle(ChatFormatting.GRAY)));
		icon.addLoreLine(owned ? Component.literal("Already yours").withStyle(ChatFormatting.RED)
				: Component.literal("Claim to own this skin").withStyle(ChatFormatting.YELLOW));
		return icon.build();
	}

	private static GuiElement pane(net.minecraft.world.item.Item item, Component name, String lore, Runnable click) {
		return new GuiElementBuilder(item).setName(name).addLoreLine(Component.literal(lore).withStyle(ChatFormatting.GRAY)).setCallback(click).build();
	}

	private static GuiElement filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.black()).setName(Component.literal(" ")).hideTooltip().build();
	}

	/** The claim chest; closing it starts the quiet time, so a still-held button doesn't reopen it at once. */
	private static final class Gui extends SimpleGui {
		Gui(ServerPlayer player) {
			super(MenuType.GENERIC_9x3, player, false);
			setLockPlayerInventory(true);
		}

		@Override
		public void onManualClose() {
			closed();
		}

		@Override
		public void onPlayerClose(boolean success) {
			closed();
		}

		private void closed() {
			LAST.put(getPlayer(), getPlayer().level().getServer().getTickCount());
		}
	}
}

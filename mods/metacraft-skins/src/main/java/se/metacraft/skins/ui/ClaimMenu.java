package se.metacraft.skins.ui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * The Claim: right-clicking a Skin Token in the main hand (on air, a block or an entity) opens
 * "Claim &lt;Skin&gt;?" ({@link RightClick}), a 3-row chest built with sgui. Its middle row holds two green claim panes, the
 * skin with its Look and Kind, and two red cancel panes; everything else is filler. For a skin the
 * player already owns the claim panes are gray, the title is "Already owned", and the token stays
 * theirs to trade. Only ever calls the {@link Skins} module; the player's own inventory is locked.
 */
public final class ClaimMenu {
	private ClaimMenu() {}

	static final int[] CLAIM = {10, 11}, CANCEL = {15, 16};
	static final int SKIN = 13;
	private static final Feedback.Audience CLAIMING = new Feedback.Audience.Owner("claim", "you keep the token");

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
		for (int slot = 0; slot < gui.getSize(); slot++) gui.setSlot(slot, Panes.filler(Items.STAINED_GLASS_PANE.black()));
		for (int slot : CLAIM) {
			if (owned) {
				gui.setSlot(slot, Panes.button(Items.STAINED_GLASS_PANE.gray(), Component.literal("You already own " + name).withStyle(ChatFormatting.GRAY),
						"Keep the token to trade it", () -> Feedback.failed(player, Feedback.whyNot(OwnershipChange.ALREADY_OWNED, name, CLAIMING))));
			} else {
				gui.setSlot(slot, Panes.button(Items.STAINED_GLASS_PANE.lime(), Component.literal("✔ Claim " + name).withStyle(ChatFormatting.GREEN),
						"The token is used up", () -> claim(player, skins, gui, token, name)));
			}
		}
		for (int slot : CANCEL) {
			gui.setSlot(slot, Panes.button(Items.STAINED_GLASS_PANE.red(), Component.literal("✖ Cancel").withStyle(ChatFormatting.RED), "You keep the token", () -> {
				gui.close();
				Feedback.cancelled(player, "Claim", "the token");
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

	/** The claim chest ({@link RightClick.Menu}: closing it starts the quiet time). */
	private static final class Gui extends RightClick.Menu {
		Gui(ServerPlayer player) {
			super(player);
		}
	}
}

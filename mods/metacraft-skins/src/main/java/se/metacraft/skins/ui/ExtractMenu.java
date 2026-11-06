package se.metacraft.skins.ui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import se.metacraft.skins.OwnershipChange;
import se.metacraft.skins.SkinSummary;
import se.metacraft.skins.Skins;
import se.metacraft.skins.item.SkinToken;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * The Extract screen, opened from the Skin Menu's "Extract: choose a skin": "Extract &lt;Skin&gt;?", a
 * 3-row chest built like the claim menu. Its middle row holds two green extract panes, the Skin Token
 * the Owner would get, and two red cancel panes; everything else is filler. Nothing changes until
 * Extract is pressed, which checks again ({@link Skins#extract}). Cancel and Extract, done or refused,
 * both go back to where it was opened from; Escape just closes it. Only ever calls the {@link Skins}
 * module; the player's own inventory is locked.
 */
public final class ExtractMenu {
	private ExtractMenu() {}

	static final int[] EXTRACT = {10, 11}, CANCEL = {15, 16};
	static final int TOKEN = 13;
	private static final Feedback.Audience EXTRACTING = new Feedback.Audience.Owner("extract", "nothing changed");

	/**
	 * Opens the screen for one of the player's skins; the caller checks first that it could be extracted
	 * ({@link Skins#extractable}). {@code back} reopens the screen it was opened from, after Cancel or Extract.
	 */
	public static void open(ServerPlayer player, Skins skins, String skinId, Runnable back) {
		String name = skins.skin(skinId).map(SkinSummary::displayName).orElse(skinId);
		SimpleGui gui = SkinMenu.locked(MenuType.GENERIC_9x3, player);
		gui.setTitle(Component.literal("Extract " + name + "?"));
		for (int slot = 0; slot < gui.getSize(); slot++) gui.setSlot(slot, Panes.filler(Items.STAINED_GLASS_PANE.black()));
		for (int slot : EXTRACT) {
			gui.setSlot(slot, Panes.button(Items.STAINED_GLASS_PANE.lime(), Component.literal("✔ Extract " + name).withStyle(ChatFormatting.GREEN),
					"You get its Skin Token back", () -> extract(player, skins, gui, skinId, name, back)));
		}
		for (int slot : CANCEL) {
			gui.setSlot(slot, Panes.button(Items.STAINED_GLASS_PANE.red(), Component.literal("✖ Cancel").withStyle(ChatFormatting.RED), "You keep the skin", () -> {
				if (!gui.isOpen()) return;
				gui.close();
				Feedback.cancelled(player, "Extract", "the skin");
				back.run();
			}));
		}
		gui.setSlot(TOKEN, token(skins, skinId));
		gui.open();
		Feedback.pageTurn(player);
	}

	/** Wording a refusal, the skin by its name. */
	public static String whyNot(OwnershipChange result, String name) {
		return Feedback.whyNot(result, name, EXTRACTING);
	}

	private static void extract(ServerPlayer player, Skins skins, SimpleGui gui, String skinId, String name, Runnable back) {
		// sgui calls back for every click kind, a double-click's second click too: only the first, which closes the screen, counts.
		if (!gui.isOpen()) return;
		gui.close();
		Skins.Extracted extracted = skins.extract(player, skinId);
		if (extracted.result() != OwnershipChange.DONE) {
			Feedback.failed(player, whyNot(extracted.result(), name));
		} else {
			Feedback.extracted(player, name, extracted.dropped());
			SkinToken.resend(player);   // their other tokens for it no longer say it's owned
		}
		back.run();
	}

	/** The token they'd get, named and described as a non-Owner reads it, with what Extract is for in place of "Right-click to claim". */
	private static GuiElement token(Skins skins, String skinId) {
		ItemStack shown = skins.tokenView(SkinToken.of(skinId, 1), new ItemStack(Items.PAPER), null);
		shown.set(DataComponents.ITEM_MODEL, SkinToken.MODEL);
		List<Component> lines = new ArrayList<>(shown.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		if (!lines.isEmpty()) lines.removeLast();
		lines.add(Component.literal("Extract to trade this skin as a token").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
		shown.set(DataComponents.LORE, new ItemLore(lines));
		return GuiElementBuilder.from(shown).build();
	}

}

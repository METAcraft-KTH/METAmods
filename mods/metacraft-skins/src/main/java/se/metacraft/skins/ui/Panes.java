package se.metacraft.skins.ui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** The glass panes the mod's chest menus are built of. */
final class Panes {
	private Panes() {}

	/** A pane that does something when clicked: its name, and one gray line saying what. */
	static GuiElement button(Item pane, Component name, String lore, Runnable click) {
		return new GuiElementBuilder(pane).setName(name).addLoreLine(Component.literal(lore).withStyle(ChatFormatting.GRAY)).setCallback(click).build();
	}

	/** A pane that only fills a slot, with no tooltip. */
	static GuiElement filler(Item pane) {
		return new GuiElementBuilder(pane).setName(Component.literal(" ")).hideTooltip().build();
	}
}

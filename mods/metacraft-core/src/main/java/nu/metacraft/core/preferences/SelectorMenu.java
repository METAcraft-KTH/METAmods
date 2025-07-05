package nu.metacraft.core.preferences;

import eu.pb4.sgui.api.GuiHelpers;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import nu.metacraft.core.gui.PagedLayer;

public class SelectorMenu extends LayeredGui {

	private static ScreenHandlerType<?> getSize(int size) {
		if (size <= 9) {
			return ScreenHandlerType.GENERIC_9X1;
		} else if (size <= 9*2) {
			return ScreenHandlerType.GENERIC_9X2;
		} else if (size <= 9*3) {
			return ScreenHandlerType.GENERIC_9X3;
		} else if (size <= 9*4) {
			return ScreenHandlerType.GENERIC_9X4;
		} else if (size <= 9*5) {
			return ScreenHandlerType.GENERIC_9X5;
		} else {
			return ScreenHandlerType.GENERIC_9X6;
		}
	}

	protected final PagedLayer pages;

	public SelectorMenu(int size, ServerPlayerEntity player) {
		super(getSize(size), player, true);
		pages = new PagedLayer(
				GuiHelpers.getHeight(getType()), GuiHelpers.getWidth(getType()),
				GuiElementBuilder.from(new ItemStack(Items.OBSIDIAN)),
				GuiElementBuilder.from(new ItemStack(Items.OBSIDIAN)),
				GuiElementBuilder.from(ItemStack.EMPTY).build()
		);
		this.addLayer(pages, 0, 0);
	}


}

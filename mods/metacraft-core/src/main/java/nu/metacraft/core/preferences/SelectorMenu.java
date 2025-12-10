package nu.metacraft.core.preferences;

import eu.pb4.sgui.api.GuiHelpers;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nu.metacraft.core.gui.PagedLayer;

public class SelectorMenu extends LayeredGui {

	private static MenuType<?> getSize(int size) {
		if (size <= 9) {
			return MenuType.GENERIC_9x1;
		} else if (size <= 9*2) {
			return MenuType.GENERIC_9x2;
		} else if (size <= 9*3) {
			return MenuType.GENERIC_9x3;
		} else if (size <= 9*4) {
			return MenuType.GENERIC_9x4;
		} else if (size <= 9*5) {
			return MenuType.GENERIC_9x5;
		} else {
			return MenuType.GENERIC_9x6;
		}
	}

	protected final PagedLayer pages;

	public SelectorMenu(int size, ServerPlayer player) {
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

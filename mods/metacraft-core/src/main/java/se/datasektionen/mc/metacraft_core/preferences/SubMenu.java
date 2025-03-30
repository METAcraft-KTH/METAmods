package se.datasektionen.mc.metacraft_core.preferences;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.SlotGuiInterface;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Function;

public class SubMenu extends SelectorMenu {

	private final SlotGuiInterface parentMenu;
	private final Function<SubMenu, List<GuiElementInterface>> buttonCreator;

	public SubMenu(
			SlotGuiInterface parentMenu, ServerPlayerEntity player,
			Function<SubMenu, List<GuiElementInterface>> buttonCreator, int buttonCount,
			Text menuTitle
	) {
		super(buttonCount, player);
		this.parentMenu = parentMenu;
		this.setTitle(menuTitle);
		this.buttonCreator = buttonCreator;
		refreshButtons();
	}

	public void refreshButtons() {
		pages.setElements(buttonCreator.apply(this));
	}

	@Override
	public void onClose() {
		super.onClose();
		parentMenu.open();
	}
}

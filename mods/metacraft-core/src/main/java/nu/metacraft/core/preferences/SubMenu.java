package nu.metacraft.core.preferences;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.SlotBasedGui;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SubMenu extends SelectorMenu {

	private final SlotBasedGui parentMenu;
	private final Function<SubMenu, List<GuiElement>> buttonCreator;

	public SubMenu(
			SlotBasedGui parentMenu, ServerPlayer player,
			Function<SubMenu, List<GuiElement>> buttonCreator, int buttonCount,
			Component menuTitle
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
	public void onManualClose() {
		super.onManualClose();
		parentMenu.open();
	}
}

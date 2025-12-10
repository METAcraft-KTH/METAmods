package nu.metacraft.core.preferences;

import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.SlotGuiInterface;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SubMenu extends SelectorMenu {

	private final SlotGuiInterface parentMenu;
	private final Function<SubMenu, List<GuiElementInterface>> buttonCreator;

	public SubMenu(
			SlotGuiInterface parentMenu, ServerPlayer player,
			Function<SubMenu, List<GuiElementInterface>> buttonCreator, int buttonCount,
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
	public void onClose() {
		super.onClose();
		parentMenu.open();
	}
}

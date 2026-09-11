package nu.metacraft.plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public abstract class PaymentMenu extends LayeredGui {

	protected Layer input = new Layer(3, 9);
	protected Container inventory = new SimpleContainer(input.getSize());

	protected abstract GuiElementBuilder createPaymentButton();

	protected Slot createSlot(int index) {
		return new Slot(inventory, index, 0, 0);
	}

	protected GuiElementBuilder createCloseButton() {
		return GuiElementBuilder.from(new ItemStack(Items.BARRIER)).setName(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.close", "Close"
				)
		).setCallback(() -> {
			close();
		});
	}

	public PaymentMenu(ServerPlayer player) {
		super(MenuType.GENERIC_9x4, player, false);

		for (int i = 0; i < input.getSize(); i++) {
			input.setSlot(i, createSlot(i));
		}
		this.addLayer(input, 0, 0);
		Layer buttons = new Layer(1, 3);
		buttons.setSlot(0, createPaymentButton());
		buttons.setSlot(2, createCloseButton());
		this.addLayer(buttons, 3, 3);
	}

	@Override
	public void onManualClose() {
		super.onManualClose();
		if (getPlayer().isDeadOrDying() || getPlayer().hasDisconnected()) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				getPlayer().drop(inventory.removeItemNoUpdate(i), false, Prediction.SERVER_ONLY);
			}
		} else {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				getPlayer().getInventory().placeItemBackInInventory(inventory.removeItemNoUpdate(i), Prediction.SERVER_ONLY);
			}
		}
	}
}

package nu.metacraft.plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public abstract class PaymentMenu extends LayeredGui {

	protected Layer input = new Layer(3, 9);
	protected Inventory inventory = new SimpleInventory(input.getSize());

	protected abstract GuiElementBuilder createPaymentButton();

	protected Slot createSlot(int index) {
		return new Slot(inventory, index, 0, 0);
	}

	protected GuiElementBuilder createCloseButton() {
		return GuiElementBuilder.from(new ItemStack(Items.BARRIER)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.close", "Close"
				)
		).setCallback((index, type, action) -> {
			close();
		});
	}

	public PaymentMenu(ServerPlayerEntity player) {
		super(ScreenHandlerType.GENERIC_9X4, player, false);

		for (int i = 0; i < input.getSize(); i++) {
			input.setSlotRedirect(i, createSlot(i));
		}
		this.addLayer(input, 0, 0);
		Layer buttons = new Layer(1, 3);
		buttons.setSlot(0, createPaymentButton());
		buttons.setSlot(2, createCloseButton());
		this.addLayer(buttons, 3, 3);
	}

	@Override
	public void onClose() {
		super.onClose();
		if (getPlayer().isDead() || getPlayer().isDisconnected()) {
			for (int i = 0; i < inventory.size(); i++) {
				getPlayer().dropItem(inventory.removeStack(i), false);
			}
		} else {
			for (int i = 0; i < inventory.size(); i++) {
				getPlayer().getInventory().offerOrDrop(inventory.removeStack(i));
			}
		}
	}
}

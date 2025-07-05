package nu.metacraft.plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nu.metacraft.plots.zone.PlayerOwnedProtectorate;

public class PlotPaymentMenu extends PaymentMenu {

	private final PlayerOwnedProtectorate protectorate;

	public PlotPaymentMenu(ServerPlayerEntity player, PlayerOwnedProtectorate protectorate) {
		super(player);
		this.setTitle(Text.translatableWithFallback(
				"protectorate.metacraft.gui.pay", "Payment"
		));
		this.protectorate = protectorate;
	}

	@Override
	protected Slot createSlot(int index) {
		return new Slot(inventory, index, 0, 0) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return protectorate.isValidIncrementItem(stack);
			}
		};
	}

	@Override
	protected GuiElementBuilder createPaymentButton() {
		return GuiElementBuilder.from(new ItemStack(Items.DIAMOND)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.pay.pay", "Pay"
				)
		).setCallback((index, type, action) -> {
			for (int i = 0; i < inventory.size(); i++) {
				protectorate.applyIncrementItem(inventory.getStack(i));
				if (inventory.getStack(i).isEmpty()) {
					inventory.removeStack(i);
				}
			}
		});
	}
}

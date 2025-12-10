package nu.metacraft.plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nu.metacraft.plots.zone.PlayerOwnedProtectorate;

public class PlotPaymentMenu extends PaymentMenu {

	private final PlayerOwnedProtectorate protectorate;

	public PlotPaymentMenu(ServerPlayer player, PlayerOwnedProtectorate protectorate) {
		super(player);
		this.setTitle(Component.translatableWithFallback(
				"protectorate.metacraft.gui.pay", "Payment"
		));
		this.protectorate = protectorate;
	}

	@Override
	protected Slot createSlot(int index) {
		return new Slot(inventory, index, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return protectorate.isValidIncrementItem(stack);
			}
		};
	}

	@Override
	protected GuiElementBuilder createPaymentButton() {
		return GuiElementBuilder.from(new ItemStack(Items.DIAMOND)).setName(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.pay.pay", "Pay"
				)
		).setCallback((index, type, action) -> {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				protectorate.applyIncrementItem(inventory.getItem(i));
				if (inventory.getItem(i).isEmpty()) {
					inventory.removeItemNoUpdate(i);
				}
			}
		});
	}
}

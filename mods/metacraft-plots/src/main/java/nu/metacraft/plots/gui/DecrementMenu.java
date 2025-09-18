package nu.metacraft.plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nu.metacraft.plots.zone.PlotDataTypes;
import nu.metacraft.zones.ZoneManager;

public class DecrementMenu extends PaymentMenu {
	
	public DecrementMenu(ServerPlayerEntity player) {
		super(player);
		this.setTitle(Text.translatableWithFallback(
				"protectorate.metacraft.gui.deliver_head", "Deliver Head"
		));
	}

	@Override
	protected GuiElementBuilder createPaymentButton() {
		return GuiElementBuilder.from(new ItemStack(Items.DIAMOND_SWORD)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.deliver_head.deliver", "Deliver"
				)
		).setCallback((index, type, action) -> {
			for (var zone : ZoneManager.getInstance(getPlayer().getEntityWorld().getServer()).getZones().getZones()) {
				zone.get(PlotDataTypes.PLAYER_PROTECTORATE).ifPresent(protectorate -> {
					for (int i = 0; i < inventory.size(); i++) {
						protectorate.applyDecrementItem(inventory.getStack(i));
						if (inventory.getStack(i).isEmpty()) {
							inventory.removeStack(i);
						}
					}
					if (protectorate.getBalance() == 0) {
						getPlayer().sendMessage(
								Text.translatableWithFallback(
										"protectorate.metacraft.gui.deliver_head.protection_gone",
										"The protection for " + protectorate.getZone().getName() + " is now gone!",
										protectorate.getZone().getName()
								)
						);
					}
				});
			}
		});
	}
}

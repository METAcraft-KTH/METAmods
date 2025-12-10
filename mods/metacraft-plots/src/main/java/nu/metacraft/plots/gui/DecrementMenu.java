package nu.metacraft.plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nu.metacraft.plots.zone.PlotDataTypes;
import nu.metacraft.zones.ZoneManager;

public class DecrementMenu extends PaymentMenu {
	
	public DecrementMenu(ServerPlayer player) {
		super(player);
		this.setTitle(Component.translatableWithFallback(
				"protectorate.metacraft.gui.deliver_head", "Deliver Head"
		));
	}

	@Override
	protected GuiElementBuilder createPaymentButton() {
		return GuiElementBuilder.from(new ItemStack(Items.DIAMOND_SWORD)).setName(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.deliver_head.deliver", "Deliver"
				)
		).setCallback((index, type, action) -> {
			for (var zone : ZoneManager.getInstance(getPlayer().level().getServer()).getZones().getZones()) {
				zone.get(PlotDataTypes.PLAYER_PROTECTORATE).ifPresent(protectorate -> {
					for (int i = 0; i < inventory.getContainerSize(); i++) {
						protectorate.applyDecrementItem(inventory.getItem(i));
						if (inventory.getItem(i).isEmpty()) {
							inventory.removeItemNoUpdate(i);
						}
					}
					if (protectorate.getBalance() == 0) {
						getPlayer().sendSystemMessage(
								Component.translatableWithFallback(
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

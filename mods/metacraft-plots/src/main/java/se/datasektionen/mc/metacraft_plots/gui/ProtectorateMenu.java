package se.datasektionen.mc.metacraft_plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.util.helper.SGUIHelper;

public class ProtectorateMenu extends SimpleGui {
	public ProtectorateMenu(ServerPlayerEntity player) {
		super(ScreenHandlerType.HOPPER, player, true);
		this.setTitle(Text.translatableWithFallback(
				"protectorate.metacraft.gui.title", "Protectorate Management"
		));

		this.setSlot(
			1, GuiElementBuilder.from(new ItemStack(Items.DIAMOND_BLOCK)).setName(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.pay", "Pay"
				)
			).setCallback((index, type, action) -> {
				this.close();
				new ChooseProtectorateMenu(player, protectorate -> {
					new PlotPaymentMenu(player, protectorate).open();
					return true;
				}).open();
			})
		);
		this.setSlot(
			3, SGUIHelper.createPlayerHeadIcon(
				Text.translatableWithFallback(
						"protectorate.metacraft.gui.deliver_head", "Deliver Head"
				),
				getPlayer().getServer(),
				player.getServer().getPlayerManager().getPlayerList().stream().filter(
						p -> !p.isSpectator() && !p.isCreative() && !p.hasPermissionLevel(1) &&
								!Permissions.check(p, "metacraft.zone.protectorates.hide_from_gui")
				)
			).setCallback((index, type, action) -> {
				this.close();
				new DecrementMenu(player).open();
			})
		);
	}
}

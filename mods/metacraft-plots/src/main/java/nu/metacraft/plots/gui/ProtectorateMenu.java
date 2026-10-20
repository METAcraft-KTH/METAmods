package nu.metacraft.plots.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nu.metacraft.core.util.helper.SGUIHelper;
import nu.metacraft.lib.METAcraftLib;

public class ProtectorateMenu extends SimpleGui {
	public ProtectorateMenu(ServerPlayer player) {
		super(MenuType.HOPPER, player, true);
		this.setTitle(Component.translatableWithFallback(
				"protectorate.metacraft.gui.title", "Protectorate Management"
		));

		this.setSlot(
			1, GuiElementBuilder.from(new ItemStack(Items.DIAMOND_BLOCK)).setName(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.pay", "Pay"
				)
			).setCallback(() -> {
				this.close();
				new ChooseProtectorateMenu(player, protectorate -> {
					new PlotPaymentMenu(player, protectorate).open();
					return true;
				}).open();
			})
		);
		this.setSlot(
			3, SGUIHelper.createPlayerHeadIcon(
				Component.translatableWithFallback(
						"protectorate.metacraft.gui.deliver_head", "Deliver Head"
				),
				player.level().getServer().getPlayerList().getPlayers().stream().filter(
						p -> !p.isSpectator() && !p.isCreative() &&
								!p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR) &&
								!p.createCommandSourceStack().checkPermission(METAcraftLib.getID("zone.protectorates.hide_from_gui"), false)
				)
			).setCallback(() -> {
				this.close();
				new DecrementMenu(player).open();
			})
		);
	}
}

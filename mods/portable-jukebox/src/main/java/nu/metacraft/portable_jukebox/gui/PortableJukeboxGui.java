package nu.metacraft.portable_jukebox.gui;

import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TooltipDisplay;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.item.PortableJukeboxItem;

public class PortableJukeboxGui extends LayeredGui {

	private final PortableJukeboxInventory portableJukebox;

	public static PortableJukeboxGui create(ServerPlayer player, ItemStack portableJukebox, EntityRef toPlayFrom) {
		return new PortableJukeboxGui(player, portableJukebox, toPlayFrom, toPlayFrom::onUpdate);
	}

	public PortableJukeboxGui(
			ServerPlayer player, ItemStack portableJukebox, EntityRef toPlayFrom, Runnable onUpdate
	) {
		super(MenuType.GENERIC_9x1, player, false);

		this.portableJukebox = new PortableJukeboxInventory(portableJukebox, onUpdate);

		this.setTitle(Component.translatableWithFallback(
				"portable_jukebox.gui", "Portable Jukebox"
		));

		Layer slot = new Layer(1, 1);
		slot.setSlot(0, new Slot(this.portableJukebox, 0, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(DataComponents.JUKEBOX_PLAYABLE);
			}

			@Override
			protected void onSwapCraft(int amount) {
				PortableJukeboxItem.stop(portableJukebox, player.level());
			}
		});
		Layer buttons = new Layer(1, 2);
		buttons.setSlot(
				0, new ItemStack(
					Items.GREEN_WOOL.builtInRegistryHolder(), 1, DataComponentPatch.builder().set(
						DataComponents.ITEM_NAME, Component.literal("Play")
					).build()
				),
				() -> {
					PortableJukeboxItem.play(portableJukebox, toPlayFrom);
				}
		);
		buttons.setSlot(
				1,
				new ItemStack(
						Items.RED_WOOL.builtInRegistryHolder(), 1, DataComponentPatch.builder().set(
							DataComponents.ITEM_NAME, Component.literal("Stop")
						).build()
				),
				() -> {
					PortableJukeboxItem.stop(portableJukebox, player.level());
				}
		);

		setBackground(new ItemStack(
				Items.ORANGE_STAINED_GLASS_PANE.builtInRegistryHolder(), 1, DataComponentPatch.builder().set(
						DataComponents.ITEM_NAME, Component.literal("")
				).set(
						DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, ReferenceSortedSets.emptySet())
				).build()
		));

		this.addLayer(slot, 3, 0);
		this.addLayer(buttons, 7, 0);
	}

	private void setBackground(ItemStack background) {
		for (int i = 0; i < getSize(); i++) {
			this.setSlot(i, background);
		}
	}

	@Override
    public void onTick() {
		if (portableJukebox.getJukebox().isEmpty()) {
			close();
			return;
		}
        super.onTick();
    }
}

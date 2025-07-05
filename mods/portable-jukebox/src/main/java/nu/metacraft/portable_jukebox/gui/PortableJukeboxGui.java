package nu.metacraft.portable_jukebox.gui;

import eu.pb4.sgui.api.gui.layered.Layer;
import eu.pb4.sgui.api.gui.layered.LayeredGui;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.item.PortableJukeboxItem;

public class PortableJukeboxGui extends LayeredGui {

	private final PortableJukeboxInventory portableJukebox;

	public static PortableJukeboxGui create(ServerPlayerEntity player, ItemStack portableJukebox, EntityRef toPlayFrom) {
		return new PortableJukeboxGui(player, portableJukebox, toPlayFrom, toPlayFrom::onUpdate);
	}

	public PortableJukeboxGui(
			ServerPlayerEntity player, ItemStack portableJukebox, EntityRef toPlayFrom, Runnable onUpdate
	) {
		super(ScreenHandlerType.GENERIC_9X1, player, false);

		this.portableJukebox = new PortableJukeboxInventory(portableJukebox, onUpdate);

		this.setTitle(Text.translatableWithFallback(
				"portable_jukebox.gui", "Portable Jukebox"
		));

		Layer slot = new Layer(1, 1);
		slot.setSlotRedirect(0, new Slot(this.portableJukebox, 0, 0, 0) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.contains(DataComponentTypes.JUKEBOX_PLAYABLE);
			}

			@Override
			protected void onTake(int amount) {
				PortableJukeboxItem.stop(portableJukebox, player.getWorld());
			}
		});
		Layer buttons = new Layer(1, 2);
		buttons.setSlot(
				0, new ItemStack(
					Items.GREEN_WOOL.getRegistryEntry(), 1, ComponentChanges.builder().add(
						DataComponentTypes.ITEM_NAME, Text.literal("Play")
					).build()
				),
				(index, type, action) -> {
					PortableJukeboxItem.play(portableJukebox, toPlayFrom);
				}
		);
		buttons.setSlot(
				1,
				new ItemStack(
						Items.RED_WOOL.getRegistryEntry(), 1, ComponentChanges.builder().add(
							DataComponentTypes.ITEM_NAME, Text.literal("Stop")
						).build()
				),
				(index, type, action) -> {
					PortableJukeboxItem.stop(portableJukebox, player.getWorld());
				}
		);

		setBackground(new ItemStack(
				Items.ORANGE_STAINED_GLASS_PANE.getRegistryEntry(), 1, ComponentChanges.builder().add(
						DataComponentTypes.ITEM_NAME, Text.literal("")
				).add(
						DataComponentTypes.TOOLTIP_DISPLAY, new TooltipDisplayComponent(true, ReferenceSortedSets.emptySet())
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

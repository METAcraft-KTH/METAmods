package se.metacraft.portable_jukebox.item;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.metacraft.portable_jukebox.upgrade.JukeboxUpgrades;

import java.util.List;

/**
 * A Portable Jukebox upgrade of one tier. A vanilla client sees a glinting disc fragment.
 */
public class JukeboxUpgradeItem extends SimplePolymerItem {

	private final int tier;

	public JukeboxUpgradeItem(Properties settings, int tier) {
		super(settings, Items.DISC_FRAGMENT_5);
		this.tier = tier;
	}

	public int getTier() {
		return tier;
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		int added = JukeboxUpgrades.slotsAdded(tier);
		if (added > 0) {
			tooltip.add(Component.translatable(
				tier == 1 ? "item.portable_jukebox.jukebox_upgrade.tooltip.slots" : "item.portable_jukebox.jukebox_upgrade.tooltip.more_slots", added
			).withStyle(ChatFormatting.GRAY));
		}
		if (tier == JukeboxUpgrades.SHUFFLE_TIER) {
			tooltip.add(Component.translatable("item.portable_jukebox.jukebox_upgrade.tooltip.shuffle").withStyle(ChatFormatting.GRAY));
		}
		if (tier == JukeboxUpgrades.CONFIGURATION_TIER) {
			tooltip.add(Component.translatable("item.portable_jukebox.jukebox_upgrade.tooltip.configuration").withStyle(ChatFormatting.GRAY));
		}
	}
}

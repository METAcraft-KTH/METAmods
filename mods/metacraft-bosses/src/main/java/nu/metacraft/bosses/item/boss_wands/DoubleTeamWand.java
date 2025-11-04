package nu.metacraft.bosses.item.boss_wands;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import nu.metacraft.bosses.item.components.BossComponents;
import nu.metacraft.bosses.util.DoubleTeamHandler;
import xyz.nucleoid.packettweaker.PacketContext;

public class DoubleTeamWand extends Item implements PolymerItem {
	public DoubleTeamWand(net.minecraft.world.item.Item.Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		var settings = user.getItemInHand(hand).get(BossComponents.DOUBLE_TEAM_SETTINGS);
		if (settings == null) {
			return InteractionResult.FAIL;
		}
		var handler = new DoubleTeamHandler(
				user, settings, 0, 0
		);
		DoubleTeamHandler.applyToEntity(handler);
		return InteractionResult.SUCCESS_SERVER.withoutItem();
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext packetContext) {
		return Items.BLAZE_ROD;
	}
}

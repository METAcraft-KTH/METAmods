package se.datasektionen.mc.metacraft_season_4.item.boss_wands;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_season_4.item.components.Season4Components;
import se.datasektionen.mc.metacraft_season_4.util.DoubleTeamHandler;
import xyz.nucleoid.packettweaker.PacketContext;

public class DoubleTeamWand extends Item implements PolymerItem {
	public DoubleTeamWand(net.minecraft.item.Item.Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		var settings = user.getStackInHand(hand).get(Season4Components.DOUBLE_TEAM_SETTINGS);
		if (settings == null) {
			return ActionResult.FAIL;
		}
		var handler = new DoubleTeamHandler(
				user, settings, 0, 0
		);
		DoubleTeamHandler.applyToEntity(handler);
		return ActionResult.SUCCESS_SERVER.noIncrementStat();
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext packetContext) {
		return Items.BLAZE_ROD;
	}
}

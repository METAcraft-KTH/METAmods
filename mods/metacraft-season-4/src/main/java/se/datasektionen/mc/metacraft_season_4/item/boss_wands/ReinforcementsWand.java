package se.datasektionen.mc.metacraft_season_4.item.boss_wands;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_season_4.item.components.Season4Components;
import xyz.nucleoid.packettweaker.PacketContext;

public class ReinforcementsWand extends Item implements PolymerItem {

	public static IntProvider DEFAULT_TRY_COUNT = UniformIntProvider.create(10, 20);

	public ReinforcementsWand(net.minecraft.item.Item.Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		var stack = user.getStackInHand(hand);
		if (world.isClient()) return ActionResult.PASS;
		if (stack.contains(Season4Components.SPAWNS)) {
			var pool = stack.get(Season4Components.SPAWNS);
			int tryCount = stack.getOrDefault(Season4Components.TRY_COUNT, DEFAULT_TRY_COUNT).get(user.getRandom());
			for (int i = 0; i < tryCount; i++) {
				pool.getOrEmpty(user.getRandom()).ifPresent(data -> {
					EntityHelper.spawnEntity(
							data, e -> true, e -> true, user.getPos(),
							(ServerWorld) user.getWorld(), user.getRandom(), user
					);
				});
			}
			return ActionResult.SUCCESS_SERVER.noIncrementStat();
		} else {
			user.sendMessage(Text.literal("This item has no spawns set!"), true);
			return ActionResult.FAIL;
		}
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext ctx) {
		return Items.BLAZE_ROD;
	}
}

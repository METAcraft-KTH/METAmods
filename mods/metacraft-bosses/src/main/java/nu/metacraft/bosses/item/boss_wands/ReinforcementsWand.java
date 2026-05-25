package nu.metacraft.bosses.item.boss_wands;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.item.components.BossComponents;

public class ReinforcementsWand extends Item implements PolymerItem {

	public static IntProvider DEFAULT_TRY_COUNT = UniformInt.of(10, 20);

	public ReinforcementsWand(net.minecraft.world.item.Item.Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		var stack = user.getItemInHand(hand);
		if (world.isClientSide()) return InteractionResult.PASS;
		if (stack.has(BossComponents.SPAWNS)) {
			var pool = stack.get(BossComponents.SPAWNS);
			int tryCount = stack.getOrDefault(BossComponents.TRY_COUNT, DEFAULT_TRY_COUNT).sample(user.getRandom());
			for (int i = 0; i < tryCount; i++) {
				pool.getRandom(user.getRandom()).ifPresent(data -> {
					EntityHelper.spawnEntity(
							data, e -> true, e -> true, user.position(),
							(ServerLevel) user.level(), user.getRandom(), user
					);
				});
			}
			return InteractionResult.SUCCESS_SERVER.withoutItem();
		} else {
			user.sendOverlayMessage(Component.literal("This item has no spawns set!"));
			return InteractionResult.FAIL;
		}
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext ctx) {
		return Items.BLAZE_ROD;
	}
}

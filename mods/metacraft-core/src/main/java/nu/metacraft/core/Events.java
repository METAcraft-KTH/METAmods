package nu.metacraft.core;

import com.google.common.collect.Sets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.core.entity.entities.MovingBlock;
import nu.metacraft.core.item.METAcraftItems;
import nu.metacraft.core.item.items.Wrench;

public class Events {

	public static void init() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity p) {
				var stack = player.getStackInHand(hand);
				if (stack.getItem() == METAcraftItems.WRENCH) {
					if (hand == Hand.MAIN_HAND) {
						var offhand = player.getOffHandStack();
						if (!offhand.isEmpty()) {
							player.getItemCooldownManager().set(offhand, 1);
						}
						return stack.useOnBlock(new ItemUsageContext(world, player, hand, player.getStackInHand(hand), hitResult));
					} else {
						return ActionResult.FAIL;
					}
				}
				if (hand == Hand.MAIN_HAND) {
					var offhand = player.getOffHandStack();
					if (offhand.getItem() == METAcraftItems.WRENCH && Wrench.canUse(world, hitResult.getBlockPos())) {
						if (offhand.useOnBlock(new ItemUsageContext(world, player, hand, offhand, hitResult)).isAccepted()) {
							player.swingHand(Hand.OFF_HAND, true);
						}
						return ActionResult.FAIL;
					}
				}
			}
			return ActionResult.PASS;
		});

		ServerTickEvents.END_WORLD_TICK.register(world -> { //Needs to run outside the general entity tick loop to avoid desync.
			for (var e : world.getEntitiesByType(TypeFilter.instanceOf(MovingBlock.class), e -> e.getRootAnchor().isPresent())) {
				e.getRootAnchor().ifPresent(anchor -> {
					var targetPos = anchor.getTargetPos();
					if (!targetPos.equals(e.getPos()) || anchor.entity().getWorld() != e.getWorld()) {
						var dist = e.squaredDistanceTo(anchor.entity());
						if (dist > MovingBlock.SQ_MAX_MOVE_DIST || anchor.entity().getWorld() != e.getWorld()) {
							e.setVelocity(Vec3d.ZERO);
							e.teleport(
									world, targetPos.getX(), targetPos.getY(), targetPos.getZ(),
									Sets.union(PositionFlag.ROT, PositionFlag.DELTA),
									0, 0, false
							);
						} else {
							var movement = targetPos.subtract(e.getPos());
							e.setVelocity(movement);
						}
						e.velocityDirty = true;
					}
				});
			}
		});
	}

}

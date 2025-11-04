package nu.metacraft.core;

import com.google.common.collect.Sets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.entity.entities.MovingBlock;
import nu.metacraft.core.item.METAcraftItems;
import nu.metacraft.core.item.items.Wrench;
import nu.metacraft.core.util.METAcraftCoreData;

public class Events {

	public static void init() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer p) {
				var stack = player.getItemInHand(hand);
				if (stack.getItem() == METAcraftItems.WRENCH) {
					if (hand == InteractionHand.MAIN_HAND) {
						var offhand = player.getOffhandItem();
						if (!offhand.isEmpty()) {
							player.getCooldowns().addCooldown(offhand, 1);
						}
						return stack.useOn(new UseOnContext(world, player, hand, player.getItemInHand(hand), hitResult));
					} else {
						return InteractionResult.FAIL;
					}
				}
				if (hand == InteractionHand.MAIN_HAND) {
					var offhand = player.getOffhandItem();
					if (offhand.getItem() == METAcraftItems.WRENCH && Wrench.canUse(world, hitResult.getBlockPos())) {
						if (offhand.useOn(new UseOnContext(world, player, hand, offhand, hitResult)).consumesAction()) {
							player.swing(InteractionHand.OFF_HAND, true);
						}
						return InteractionResult.FAIL;
					}
				}
			}
			return InteractionResult.PASS;
		});

		ServerTickEvents.END_WORLD_TICK.register(world -> { //Needs to run outside the general entity tick loop to avoid desync.
			for (var e : world.getEntities(EntityTypeTest.forClass(MovingBlock.class), e -> e.getRootAnchor().isPresent())) {
				e.getRootAnchor().ifPresent(anchor -> {
					var targetPos = anchor.getTargetPos();
					if (!targetPos.equals(e.position()) || anchor.entity().level() != e.level()) {
						var dist = e.distanceToSqr(anchor.entity());
						if (dist > MovingBlock.SQ_MAX_MOVE_DIST || anchor.entity().level() != e.level()) {
							e.setDeltaMovement(Vec3.ZERO);
							e.teleportTo(
									world, targetPos.x(), targetPos.y(), targetPos.z(),
									Sets.union(Relative.ROTATION, Relative.DELTA),
									0, 0, false
							);
						} else {
							var movement = targetPos.subtract(e.position());
							e.setDeltaMovement(movement);
						}
						e.hasImpulse = true;
					}
				});
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			METAcraftCoreData.getInstance(server).getCountdown().ifPresent(countdown-> countdown.tick(server));
		});
	}

}

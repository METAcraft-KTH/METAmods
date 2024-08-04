package se.datasektionen.mc.simplecustomfeatures;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import se.datasektionen.mc.simplecustomfeatures.objects.blocks.dynamic_portal.PortalBlockObject;

public class Events {

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ObjectCache.getInstance(server).onLoad();
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!(world instanceof ServerWorld sw) || hitResult.getType() == HitResult.Type.MISS) {
				return ActionResult.PASS;
			}
			var stack = player.getStackInHand(hand);
			return PortalBlockObject.getForItem(stack, sw).map(
					portal -> {
						return portal.findPortalShape(sw, hitResult.getBlockPos().offset(hitResult.getSide())).map(
								foundPortal -> {
									foundPortal.activate();
									if (stack.isDamageable()) {
										stack.damage(1, player, hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
									} else {
										stack.decrementUnlessCreative(1, player);
									}
									return ActionResult.SUCCESS;
								}
						).orElse(ActionResult.PASS);
					}
			).orElse(ActionResult.PASS);
		});
	}

}

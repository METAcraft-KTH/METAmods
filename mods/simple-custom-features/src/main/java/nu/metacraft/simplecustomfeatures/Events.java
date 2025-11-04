package nu.metacraft.simplecustomfeatures;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal.PortalBlockObject;

public class Events {

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ObjectCache.getInstance(server).onLoad();
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!(world instanceof ServerLevel sw) || hitResult.getType() == HitResult.Type.MISS) {
				return InteractionResult.PASS;
			}
			var stack = player.getItemInHand(hand);
			return PortalBlockObject.getForItem(stack, sw).map(
					portal -> {
						return portal.findPortalShape(sw, hitResult.getBlockPos().relative(hitResult.getDirection())).map(
								foundPortal -> {
									foundPortal.activate(world);
									if (stack.isDamageableItem()) {
										stack.hurtAndBreak(1, player, hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
									} else {
										int c = stack.getCount();
										stack.consume(1, player);
										var useRemainder = portal.getUseRemainderOverride().orElse(stack.get(DataComponents.USE_REMAINDER));
										if (useRemainder != null) {
											ItemStack itemStack = useRemainder.convertIntoRemainder(
													stack, c, player.hasInfiniteMaterials(), player::handleExtraItemsCreatedOnUse
											);
											player.setItemInHand(hand, itemStack);
										}
									}
									return (InteractionResult) InteractionResult.SUCCESS_SERVER;
								}
						).orElse(InteractionResult.PASS);
					}
			).orElse(InteractionResult.PASS);
		});
	}

}

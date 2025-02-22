package se.datasektionen.mc.metacraft_core;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.metacraft_core.item.METAcraftItems;
import se.datasektionen.mc.metacraft_core.item.components.CommandComponents;
import se.datasektionen.mc.metacraft_core.util.helper.BundleHelper;

public class Events {

	public static void init() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity p) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(CommandComponents.INTERACT_COMMAND)) {
					return CommandComponents.runCommand(p, player.getPos(), stack.get(CommandComponents.INTERACT_COMMAND));
				}
			}
			return ActionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity p) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(CommandComponents.INTERACT_BLOCK_COMMAND)) {
					return CommandComponents.runCommand(
							p, Vec3d.ofBottomCenter(hitResult.getBlockPos()),
							stack.get(CommandComponents.INTERACT_BLOCK_COMMAND)
					);
				}
				if (stack.contains(CommandComponents.INTERACT_BLOCK_SIDE_COMMAND)) {
					return CommandComponents.runCommand(
							p, Vec3d.ofBottomCenter(hitResult.getBlockPos().offset(hitResult.getSide())),
							stack.get(CommandComponents.INTERACT_BLOCK_SIDE_COMMAND)
					);
				}
				if (stack.contains(CommandComponents.INTERACT_BLOCK_EXACT_COMMAND)) {
					return CommandComponents.runCommand(
							p, hitResult.getPos(),
							stack.get(CommandComponents.INTERACT_BLOCK_EXACT_COMMAND)
					);
				}
				if (stack.getItem() == METAcraftItems.WRENCH) {
					return stack.useOnBlock(new ItemUsageContext(world, player, hand, player.getStackInHand(hand), hitResult));
				}
			}
			return ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient() && hitResult != null && player instanceof ServerPlayerEntity p) { //This event fires twice, once with a valid hitResult, then again with hitResult set to null.
				var stack = player.getStackInHand(hand);
				if (stack.contains(CommandComponents.INTERACT_ENTITY_COMMAND)) {
					return CommandComponents.runCommand(
							p, entity.getPos(),
							stack.get(CommandComponents.INTERACT_ENTITY_COMMAND)
					);
				}
				if (stack.contains(CommandComponents.INTERACT_ENTITY_EXACT_COMMAND)) {
					return CommandComponents.runCommand(
							p, hitResult.getPos(),
							stack.get(CommandComponents.INTERACT_ENTITY_EXACT_COMMAND)
					);
				}
			}
			return ActionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity p) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(CommandComponents.ATTACK_ENTITY_COMMAND)) {
					var result = CommandComponents.runCommand(
							p, entity.getPos(),
							stack.get(CommandComponents.ATTACK_ENTITY_COMMAND)
					);
					if (result.isAccepted()) {
						return ActionResult.PASS;
					} else {
						return result;
					}
				}
			}
			return ActionResult.PASS;
		});

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity p) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(CommandComponents.ATTACK_BLOCK_COMMAND)) {
					var result = CommandComponents.runCommand(
							p, Vec3d.ofBottomCenter(pos),
							stack.get(CommandComponents.ATTACK_BLOCK_COMMAND)
					);
					if (result.isAccepted()) {
						return ActionResult.PASS;
					} else {
						return result;
					}
				}
				if (stack.contains(CommandComponents.ATTACK_BLOCK_SIDE_COMMAND)) {
					var result = CommandComponents.runCommand(
							p, Vec3d.ofBottomCenter(pos.offset(direction)),
							stack.get(CommandComponents.ATTACK_BLOCK_SIDE_COMMAND)
					);
					if (result.isAccepted()) {
						return ActionResult.PASS;
					} else {
						return result;
					}
				}
			}
			return ActionResult.PASS;
		});

		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register(
				(serverStack, clientStack, ctx) -> {
					if (!(serverStack.getItem() instanceof PolymerItem) && !serverStack.contains(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP)) {
						clientStack.remove(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP);
					}
					return clientStack;
				}
		);

		BundleHelper.init();
	}

}

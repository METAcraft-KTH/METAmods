package se.datasektionen.mc.metacraft_core;

import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;

public class Events {

	public static void init() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient()) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(METAcraftComponents.INTERACT_COMMAND)) {
					var result = METAcraftComponents.runCommand(player, player.getPos(), stack.get(METAcraftComponents.INTERACT_COMMAND));
					return new TypedActionResult<>(result, player.getStackInHand(hand));
				}
			}
			return TypedActionResult.pass(ItemStack.EMPTY);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient()) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(METAcraftComponents.INTERACT_BLOCK_COMMAND)) {
					return METAcraftComponents.runCommand(
							player, Vec3d.ofBottomCenter(hitResult.getBlockPos()),
							stack.get(METAcraftComponents.INTERACT_BLOCK_COMMAND)
					);
				}
				if (stack.contains(METAcraftComponents.INTERACT_BLOCK_SIDE_COMMAND)) {
					return METAcraftComponents.runCommand(
							player, Vec3d.ofBottomCenter(hitResult.getBlockPos().offset(hitResult.getSide())),
							stack.get(METAcraftComponents.INTERACT_BLOCK_SIDE_COMMAND)
					);
				}
				if (stack.contains(METAcraftComponents.INTERACT_BLOCK_EXACT_COMMAND)) {
					return METAcraftComponents.runCommand(
							player, hitResult.getPos(),
							stack.get(METAcraftComponents.INTERACT_BLOCK_EXACT_COMMAND)
					);
				}
			}
			return ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient() && hitResult != null) { //This event fires twice, once with a valid hitResult, then again with hitResult set to null.
				var stack = player.getStackInHand(hand);
				if (stack.contains(METAcraftComponents.INTERACT_ENTITY_COMMAND)) {
					return METAcraftComponents.runCommand(
							player, entity.getPos(),
							stack.get(METAcraftComponents.INTERACT_ENTITY_COMMAND)
					);
				}
				if (stack.contains(METAcraftComponents.INTERACT_ENTITY_EXACT_COMMAND)) {
					return METAcraftComponents.runCommand(
							player, hitResult.getPos(),
							stack.get(METAcraftComponents.INTERACT_ENTITY_EXACT_COMMAND)
					);
				}
			}
			return ActionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient()) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(METAcraftComponents.ATTACK_ENTITY_COMMAND)) {
					var result = METAcraftComponents.runCommand(
							player, entity.getPos(),
							stack.get(METAcraftComponents.ATTACK_ENTITY_COMMAND)
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
			if (!world.isClient()) {
				var stack = player.getStackInHand(hand);
				if (stack.contains(METAcraftComponents.ATTACK_BLOCK_COMMAND)) {
					var result = METAcraftComponents.runCommand(
							player, Vec3d.ofBottomCenter(pos),
							stack.get(METAcraftComponents.ATTACK_BLOCK_COMMAND)
					);
					if (result.isAccepted()) {
						return ActionResult.PASS;
					} else {
						return result;
					}
				}
				if (stack.contains(METAcraftComponents.ATTACK_BLOCK_SIDE_COMMAND)) {
					var result = METAcraftComponents.runCommand(
							player, Vec3d.ofBottomCenter(pos.offset(direction)),
							stack.get(METAcraftComponents.ATTACK_BLOCK_SIDE_COMMAND)
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
	}

}

package nu.metacraft.moderation.exile.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.moderation.exile.rules.PreventInteraction;

@Mixin(ServerPlayerInteractionManager.class)
public class MixinServerPlayerInteractionManager {

	@Shadow @Final protected ServerPlayerEntity player;

	@Inject(
		method = "processBlockBreakingAction",
		at = @At("HEAD"),
		cancellable = true
	)
	public void processBlockBreakingAction(
			BlockPos pos, PlayerActionC2SPacket.Action action, Direction direction,
			int worldHeight, int sequence, CallbackInfo ci
	) {
		if (PreventInteraction.shouldCancelInteraction(player, pos)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "interactItem",
		at = @At("HEAD"),
		cancellable = true
	)
	public void interactItem(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (PreventInteraction.shouldCancelInteraction(player)) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}

	@Inject(
			method = "interactBlock",
			at = @At("HEAD"),
			cancellable = true
	)
	public void interactBlock(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
		if (PreventInteraction.shouldCancelInteraction(player, hitResult.getBlockPos())) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}

}

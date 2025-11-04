package nu.metacraft.moderation.exile.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import nu.metacraft.moderation.exile.rules.PreventInteraction;

@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerInteractionManager {

	@Shadow @Final protected ServerPlayer player;

	@Inject(
		method = "handleBlockBreakAction",
		at = @At("HEAD"),
		cancellable = true
	)
	public void processBlockBreakingAction(
			BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction,
			int worldHeight, int sequence, CallbackInfo ci
	) {
		if (PreventInteraction.shouldCancelInteraction(player, pos)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "useItem",
		at = @At("HEAD"),
		cancellable = true
	)
	public void interactItem(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (PreventInteraction.shouldCancelInteraction(player)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(
			method = "useItemOn",
			at = @At("HEAD"),
			cancellable = true
	)
	public void interactBlock(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		if (PreventInteraction.shouldCancelInteraction(player, hitResult.getBlockPos())) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

}

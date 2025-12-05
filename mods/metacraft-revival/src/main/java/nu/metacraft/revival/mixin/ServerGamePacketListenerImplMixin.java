package nu.metacraft.revival.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import nu.metacraft.core.util.helper.PlayerInventoryHelper;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import nu.metacraft.revival.util.helper.RevivalHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

	@Shadow
	public ServerPlayer player;

	@Shadow
	public abstract void teleport(double x, double y, double z, float yaw, float pitch);

	@Inject(
			method = {
					"handleContainerClick",
					"handleContainerButtonClick",
					"handleContainerSlotStateChanged"
			},
			at = @At("RETURN")
	)
	public void handleContainerClick(CallbackInfo ci) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			RevivalHelper.openRevivalMenu(player);
		}
	}

	@Unique
	private void resetBlock(BlockPos pos) {
		this.player.connection.send(new ClientboundBlockUpdatePacket(pos, player.level().getBlockState(pos)));
		player.connection.send(new ClientboundBlockDestructionPacket(player.getId(), pos, -1));
	}

	@Inject(
			method = "handlePlayerAction",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"
			),
			cancellable = true
	)
	public void handlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			switch (packet.getAction()) {
				case SWAP_ITEM_WITH_OFFHAND,RELEASE_USE_ITEM, DROP_ITEM, DROP_ALL_ITEMS -> {
					PlayerInventoryHelper.syncHandStack(player, InteractionHand.MAIN_HAND);
					PlayerInventoryHelper.syncHandStack(player, InteractionHand.OFF_HAND);
					ci.cancel();
				}
			}
		}
	}

	@Inject(
			method = "handleUseItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"
			),
			cancellable = true
	)
	public void handleUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			PlayerInventoryHelper.syncHandStack(player, packet.getHand());
			ci.cancel();
		}
	}

	@Inject(
			method = "handleUseItemOn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"
			),
			cancellable = true
	)
	public void handleUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			PlayerInventoryHelper.syncHandStack(player, packet.getHand());
			resetBlock(packet.getHitResult().getBlockPos());
			ci.cancel();
		}
	}

	@Inject(
			method = "handleInteract",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;hasClientLoaded()Z"
			),
			cancellable = true
	)
	public void handleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			PlayerInventoryHelper.syncHandStack(player, InteractionHand.MAIN_HAND);
			PlayerInventoryHelper.syncHandStack(player, InteractionHand.OFF_HAND);
			ci.cancel();
		}
	}

	@Inject(
		method = "handleMovePlayer",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;updateAwaitingTeleport()Z"
		),
		cancellable = true
	)
	public void movePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			float yRot = Mth.wrapDegrees(packet.getYRot(this.player.getYRot()));
			float xRot = Mth.wrapDegrees(packet.getXRot(this.player.getXRot()));
			this.player.absSnapRotationTo(yRot, xRot);
			var x = packet.getX(player.getX());
			var y = packet.getY(player.getY());
			var z = packet.getZ(player.getZ());
			if (x != player.getX() || y != player.getY() || z != player.getZ()) {
				this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
			}
			ci.cancel();
		}
	}

}

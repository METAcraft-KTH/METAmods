package nu.metacraft.pause.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nu.metacraft.pause.PauseData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin extends ServerCommonPacketListenerImpl {

	@Shadow
	public ServerPlayer player;

	@Shadow
	public abstract void teleport(double d, double e, double f, float g, float h);

	@Shadow
	private boolean clientIsFloating;

	@Shadow
	private boolean clientVehicleIsFloating;

	public ServerGamePacketListenerImplMixin(MinecraftServer minecraftServer, Connection connection, CommonListenerCookie commonListenerCookie) {
		super(minecraftServer, connection, commonListenerCookie);
	}

	@Unique
	private boolean isFrozen() {
		var data = PauseData.getInstance(this.server);
		return data.isPaused() && PauseData.shouldFreezeWhenPaused(player);
	}

	@WrapOperation(
			method = "handleMovePlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;absSnapRotationTo(FF)V"
			)
	)
	public void stopRot(ServerPlayer player, float yaw, float pitch, Operation<Void> original) {
		if (isFrozen()) { // Lock rotation when teleporting.
			return;
		}
		original.call(player, yaw, pitch);
	}

	@WrapOperation(
			method = "handleMovePlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V",
					ordinal = 0
			)
	)
	public void stopRotVehicle(
			ServerPlayer player, double x, double y, double z, float yaw, float pitch, Operation<Void> original
	) {
		if (isFrozen()) { // Lock vehicle rotation.
			original.call(player, x, y, z, player.getRootVehicle().getYRot(), player.getRootVehicle().getXRot());
			return;
		}
		original.call(player, x, y, z, yaw, pitch);
	}

	@Inject(
			method = "handlePlayerAction",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
					shift = At.Shift.AFTER
			),
			cancellable = true
	)
	public void handlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (isFrozen()) {
			switch(packet.getAction()) {
				// We want to let block updates through to prevent issues, we stop that in another mixin.
				case ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK, START_DESTROY_BLOCK -> {}
				case DROP_ITEM, DROP_ALL_ITEMS -> { // Client will drop item regardless of what we do, so we put it back.
					int slot = player.getInventory().getSelectedSlot();
					send(new ClientboundSetPlayerInventoryPacket(slot, player.getInventory().getItem(slot)));
					ci.cancel();
				}
				default -> ci.cancel();
			}
		}
	}

	@Inject(
			method = "handleContainerClick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
					shift = At.Shift.AFTER
			),
			cancellable = true
	)
	public void handleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		if (isFrozen()) {
			// We can't stop players from opening their inventory, but we can stop them from moving items around.
			if (packet.containerId() == 0) {
				List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
				for (var slot : packet.changedSlots().keySet()) {
					if (this.player.inventoryMenu.isValidSlotIndex(slot)) {
						packets.add(new ClientboundContainerSetSlotPacket(
								0, player.inventoryMenu.getStateId(), slot,
								player.inventoryMenu.getItems().get(slot)
						));
					}
				}
				packets.add(new ClientboundSetCursorItemPacket(player.inventoryMenu.getCarried()));
				if (packets.size() == 1) {
					send(packets.getFirst());
				} else {
					send(new ClientboundBundlePacket(packets));
				}
			}
			ci.cancel();
		}
	}

	@Inject(
			method = "handlePlayerCommand",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
					shift = At.Shift.AFTER
			),
			cancellable = true
	)
	public void handlePlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
		if (isFrozen()) {
			if (packet.getAction() == ServerboundPlayerCommandPacket.Action.START_FALL_FLYING && !player.isFallFlying()) {
				player.stopFallFlying();
			}
			ci.cancel();
		}
	}

	@Inject(
		method = {
				"handleInteract",
				"handleClientCommand",
				"handlePlayerInput",
				"handleUseItemOn",
				"handleUseItem",
				"handlePaddleBoat",
				"handleContainerButtonClick"
		},
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
				shift = At.Shift.AFTER
		),
		cancellable = true,
		require = 7
	)
	public void ignoreOtherStuff(CallbackInfo ci) {
		if (isFrozen()) {
			ci.cancel();
		}
	}

	@Inject(
			method = "handleMovePlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
			),
			cancellable = true
	)
	public void onPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (isFrozen()) {
			clientIsFloating = false; // Prevent kicked for flying.
			var yRot = packet.getYRot(player.getYRot());
			var xRot = packet.getXRot(player.getXRot());
			var x = packet.getX(player.getX());
			var y = packet.getY(player.getY());
			var z = packet.getZ(player.getZ());
			if (x != player.getX() || y != player.getY() || z != player.getZ() || xRot != player.getXRot() || yRot != player.getYRot()) {
				this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
			}
			ci.cancel();
		}
	}

	@Inject(
			method = "handleMoveVehicle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
			),
			cancellable = true
	)
	public void onVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
		if (isFrozen()) {
			clientVehicleIsFloating = false; // Prevent kicked for flying.
			var yRot = packet.yRot();
			var xRot = packet.xRot();
			var x = packet.position().x;
			var y = packet.position().y;
			var z = packet.position().z;
			if (x != player.getX() || y != player.getY() || z != player.getZ() || xRot != player.getXRot() || yRot != player.getYRot()) {
				var vehicle = player.getRootVehicle();
				send(new ClientboundBundlePacket(List.of(
						ClientboundMoveVehiclePacket.fromEntity(vehicle),
						// ClientboundMoveVehiclePacket only takes effect if the player moves, we need another packet to lock rotation.
						new ClientboundPlayerRotationPacket(player.getYRot(), false, player.getXRot(), false)
				)));
			}
			ci.cancel();
		}
	}

}

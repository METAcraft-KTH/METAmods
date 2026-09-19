package nu.metacraft.rivals.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nu.metacraft.rivals.gun.PaintWeapon;
import nu.metacraft.rivals.gun.WeaponLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every packet Rivals has to answer for itself: the two buttons no Fabric event covers — a left click at
 * thin air, and F — and the three that would move a picked weapon out of the hands it was picked for.
 *
 * <p>26.3 splits what used to be the swing packet in two: an attack on the block or the entity under
 * the crosshair (the packets Fabric's {@code AttackBlockCallback} and {@code AttackEntityCallback}
 * already cover), and {@link ServerboundPunchPacket}, which the client sends on <em>every</em> left
 * click — at a block, at an entity and at nothing at all. Nothing in the Fabric API covers that last
 * case, so a click at the sky would otherwise never reach the server as anything, which is most of
 * how a paint weapon is actually fired. The client does not repeat it while the button is held (only
 * block breaking continues), and it is sent even while an item is being used, which is what lets the
 * charger be scoped with the right button and fired with the left.
 *
 * <p>F is the other one. The swap-hands key reaches the server as a
 * {@link ServerboundPlayerActionPacket} carrying {@code SWAP_ITEM_WITH_OFFHAND}, and there is no event
 * for it either — so {@code handlePlayerAction} is injected as well, and a paint weapon in the main hand
 * turns the press into {@link PaintWeapon#swapHands} and cancels the packet. Cancelling is what makes F
 * the special button rather than a way to put the gun in the off hand: vanilla never runs, so nothing
 * moves between the hands. Anything else in hand is left to vanilla, swap and all.
 *
 * <p>The last three are {@link WeaponLock}'s: the hotbar selection (a set-carried-item that would take
 * the selection off the weapon and off the selector), the two drop actions, and a container click that
 * would move the weapon in the inventory screen. Each asks the lock a question and cancels the packet on
 * a yes; the lock is where the rules and the messages live, so these stay three lines each and the rules
 * can be tested without a client.
 *
 * <p>All of them are injected after {@code ensureRunningOnSameThread}, the same place the other Metacraft
 * mods hook their packet handlers: before it, this code would be running on the netty thread.
 * {@link PaintWeapon#leftClick} de-duplicates the tick, so the attack packet and the punch that
 * follows it are one answer rather than two.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(
			method = "handlePunch",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
					shift = At.Shift.AFTER
			)
	)
	public void rivalsLeftClick(ServerboundPunchPacket packet, CallbackInfo info) {
		PaintWeapon.leftClick(player);
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
	public void rivalsPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo info) {
		ServerboundPlayerActionPacket.Action action = packet.getAction();
		if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND && PaintWeapon.swapHands(player)) {
			info.cancel();
		} else if ((action == ServerboundPlayerActionPacket.Action.DROP_ITEM
				|| action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) && WeaponLock.refuseDrop(player)) {
			info.cancel();
		}
	}

	@Inject(
			method = "handleSetCarriedItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
					shift = At.Shift.AFTER
			),
			cancellable = true
	)
	public void rivalsSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo info) {
		if (WeaponLock.refuseSlot(player, packet.getSlot())) info.cancel();
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
	public void rivalsContainerClick(ServerboundContainerClickPacket packet, CallbackInfo info) {
		if (WeaponLock.refuseContainerClick(player, packet.slotNum(), packet.buttonNum(), packet.containerInput())) {
			info.cancel();
		}
	}
}

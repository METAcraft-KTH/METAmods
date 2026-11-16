package se.metacraft.ovvar.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.content.enchantment.Keyring;

/**
 * Keyring: a leaving player's tool goes back before the player is saved. On the server thread, unlike
 * Fabric's {@code ServerPlayConnectionEvents.DISCONNECT}, which may fire on the netty thread from
 * {@code Connection.channelInactive}.
 */
@Mixin(PlayerList.class)
public abstract class KeyringLeaveMixin {
	@Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
	private void ovvar$keyring(ServerPlayer player, CallbackInfo ci) {
		Keyring.swapBack(player);
	}
}

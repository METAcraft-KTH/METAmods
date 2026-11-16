package se.metacraft.ovvar.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.content.enchantment.Keyring;

/** Keyring: the pocket tool comes to hand before vanilla reads the held item, so instant breaks and progress use it. */
@Mixin(ServerPlayerGameMode.class)
public abstract class KeyringBreakActionMixin {
	@Shadow @Final protected ServerPlayer player;

	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
	private void ovvar$keyring(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence, CallbackInfo ci) {
		Keyring.beforeBreakAction(this.player, pos, action);
	}
}

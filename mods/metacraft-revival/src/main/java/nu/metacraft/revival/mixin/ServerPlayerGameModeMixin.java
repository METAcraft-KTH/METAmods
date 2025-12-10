package nu.metacraft.revival.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
	public void handleBlockBreakAction(
			BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face,
			int maxBuildHeight, int sequence, CallbackInfo ci
	) {
		if (((ServerPlayerExtension) player).metacraft$isUnconscious()) {
			this.player.connection.send(new ClientboundBlockUpdatePacket(pos, player.level().getBlockState(pos)));
			player.connection.send(new ClientboundBlockDestructionPacket(player.getId(), pos, -1));
			ci.cancel();
		}
	}

}

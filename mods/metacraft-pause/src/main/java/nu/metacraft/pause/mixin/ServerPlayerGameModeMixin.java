package nu.metacraft.pause.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import nu.metacraft.pause.PauseData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

	@Shadow
	@Final
	protected ServerPlayer player;

	@Shadow
	protected ServerLevel level;

	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
	public void handleBlockBreakAction(
			BlockPos blockPos, ServerboundPlayerActionPacket.Action action,
			Direction direction, int i, int j, CallbackInfo ci
	) {
		var data = PauseData.getInstance(level.getServer());
		if (data.isPaused() && PauseData.shouldFreezeWhenPaused(player)) {
			// Avoid ghost air blocks and half-broken blocks.
			this.player.connection.send(new ClientboundBundlePacket(List.of(
					new ClientboundBlockUpdatePacket(blockPos, this.level.getBlockState(blockPos)),
					new ClientboundBlockDestructionPacket(player.getId(), blockPos, 10)
			)));
			ci.cancel();
		}
	}

}

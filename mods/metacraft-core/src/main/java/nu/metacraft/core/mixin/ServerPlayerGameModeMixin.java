package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.core.util.ServerSoundType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import nu.metacraft.core.block.blocks.BlockWithDisguise;
import nu.metacraft.core.block.entities.BlockEntityWithDisguise;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

	@Shadow protected ServerLevel level;

	@Shadow
	@Final
	protected ServerPlayer player;

	@ModifyArg(
		method = "destroyBlock",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"
		)
	)
	public BlockState tryBreakBlock(BlockState actualState, @Local(argsOnly = true) BlockPos pos) {
		if (actualState.getBlock() instanceof BlockWithDisguise disguised) {
			return disguised.getBlockEntity(level, pos).map(
					BlockEntityWithDisguise::getDisplayedBlockState
			).orElse(actualState);
		}
		return actualState;
	}

	@Inject(
		method = "handleBlockBreakAction",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/MinecraftServer;isUnderSpawnProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"
		)
	)
	public void fixBreakingSoundStart(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence, CallbackInfo ci) {
		BlockState blockState = this.level.getBlockState(pos);
		if (!blockState.isAir() && blockState.getDestroyProgress(player, level, pos) < 1) {
			if (blockState.getSoundType() instanceof ServerSoundType soundType) {
				level.playSound(
					null, pos, soundType.getHitSound(), SoundSource.BLOCKS,
					(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F
				);
			}
		}
	}

}

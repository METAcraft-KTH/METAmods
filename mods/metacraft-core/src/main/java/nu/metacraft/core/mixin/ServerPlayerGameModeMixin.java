package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import nu.metacraft.core.block.blocks.BlockWithDisguise;
import nu.metacraft.core.block.entities.BlockEntityWithDisguise;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

	@Shadow protected ServerLevel level;

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
					BlockEntityWithDisguise::getBlockState
			).orElse(actualState);
		}
		return actualState;
	}

}

package nu.metacraft.minigame_util.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.minigame_util.MinigameGameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

	@Shadow protected ServerLevel level;

	@Inject(
		method = "destroyBlock",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"
		)
	)
	public void tryBreakBlock(
			BlockPos pos, CallbackInfoReturnable<Boolean> cir,
			@Local(ordinal = 0) boolean broken, @Local(ordinal = 0) BlockState blockState
	) {
		if (broken && level.getGameRules().get(MinigameGameRules.REPLACE_BROKEN_BLOCKS)) {
			level.setBlockAndUpdate(pos, blockState);
		}
	}

}

package nu.metacraft.minigame_util.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import nu.metacraft.minigame_util.MinigameGameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class MixinServerPlayerInteractionManager {

	@Shadow protected ServerWorld world;

	@Inject(
		method = "tryBreakBlock",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/item/ItemStack;postMine(Lnet/minecraft/world/World;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;)V"
		)
	)
	public void tryBreakBlock(
			BlockPos pos, CallbackInfoReturnable<Boolean> cir,
			@Local(ordinal = 0) boolean broken, @Local(ordinal = 0) BlockState blockState
	) {
		if (broken && world.getGameRules().getBoolean(MinigameGameRules.REPLACE_BROKEN_BLOCKS)) {
			world.setBlockState(pos, blockState);
		}
	}

}

package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import se.datasektionen.mc.metacraft_core.block.blocks.BlockWithDisguise;
import se.datasektionen.mc.metacraft_core.block.entities.BlockEntityWithDisguise;

@Mixin(ServerPlayerInteractionManager.class)
public class MixinServerPlayerInteractionManager {

	@Shadow protected ServerWorld world;

	@ModifyArg(
		method = "tryBreakBlock",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;canHarvest(Lnet/minecraft/block/BlockState;)Z"
		)
	)
	public BlockState tryBreakBlock(BlockState actualState, @Local(argsOnly = true) BlockPos pos) {
		if (actualState.getBlock() instanceof BlockWithDisguise disguised) {
			return disguised.getBlockEntity(world, pos).map(
					BlockEntityWithDisguise::getBlockState
			).orElse(actualState);
		}
		return actualState;
	}

}

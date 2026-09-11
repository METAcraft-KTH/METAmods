package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import nu.metacraft.core.block.blocks.BlockWithDisguise;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

	@Shadow public abstract Block getBlock();

	@Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
	public void getHardness(BlockGetter world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		if (this.getBlock() instanceof BlockWithDisguise disguised) {
			disguised.getBlockEntity(world, pos).ifPresent(entity -> {
				cir.setReturnValue(entity.getDisplayedBlockState().getDestroySpeed(world, pos));
			});
		}
	}

}

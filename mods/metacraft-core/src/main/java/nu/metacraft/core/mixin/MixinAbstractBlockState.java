package nu.metacraft.core.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.core.block.blocks.BlockWithDisguise;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class MixinAbstractBlockState {

	@Shadow public abstract Block getBlock();

	@Inject(method = "getHardness", at = @At("HEAD"), cancellable = true)
	public void getHardness(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		if (this.getBlock() instanceof BlockWithDisguise disguised) {
			disguised.getBlockEntity(world, pos).ifPresent(entity -> {
				cir.setReturnValue(entity.getBlockState().getHardness(world, pos));
			});
		}
	}

}

package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.core.block.blocks.BlockWithDisguise;

@Mixin(ExplosionDamageCalculator.class)
public class ExplosionDamageCalculatorMixin {

	@ModifyExpressionValue(
			method = "getBlockExplosionResistance",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance()F"
			)
	)
	public float getBlastResistance(
			float original, @Local(argsOnly = true) BlockGetter world,
			@Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) BlockState blockState
	) {
		if (blockState.getBlock() instanceof BlockWithDisguise disguised) {
			return disguised.getBlockEntity(world, pos).map(
					entity -> entity.getDisplayedBlockState().getBlock().getExplosionResistance()
			).orElse(original);
		}
		return original;
	}

}

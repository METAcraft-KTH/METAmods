package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_core.block.blocks.BlockWithDisguise;

@Mixin(ExplosionBehavior.class)
public class MixinExplosionBehavior {

	@ModifyExpressionValue(
			method = "getBlastResistance",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/block/Block;getBlastResistance()F"
			)
	)
	public float getBlastResistance(
			float original, @Local(argsOnly = true) BlockView world,
			@Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) BlockState blockState
	) {
		if (blockState.getBlock() instanceof BlockWithDisguise disguised) {
			return disguised.getBlockEntity(world, pos).map(
					entity -> entity.getBlockState().getBlock().getBlastResistance()
			).orElse(original);
		}
		return original;
	}

}

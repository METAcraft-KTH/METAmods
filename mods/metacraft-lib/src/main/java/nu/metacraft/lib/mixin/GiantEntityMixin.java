package nu.metacraft.lib.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Giant.class)
public class GiantEntityMixin {

	@Inject(method = "getWalkTargetValue", at = @At("HEAD"), cancellable = true)
	public void getPathfindingFavor(BlockPos pos, LevelReader world, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(0.0f);
	}

}

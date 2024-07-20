package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GiantEntity.class)
public class MixinGiantEntity {

	@Inject(method = "getPathfindingFavor", at = @At("HEAD"), cancellable = true)
	public void getPathfindingFavor(BlockPos pos, WorldView world, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(0.0f);
	}

}

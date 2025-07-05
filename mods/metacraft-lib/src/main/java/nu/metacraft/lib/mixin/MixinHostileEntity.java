package nu.metacraft.lib.mixin;

import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.extensions.HostileEntityExtensions;

@Mixin(HostileEntity.class)
public class MixinHostileEntity implements HostileEntityExtensions {

	@Unique
	private boolean survivesSunlight = false;

	@Inject(
		method = "getPathfindingFavor", at = @At("HEAD"),
		cancellable = true
	)
	public void noFearDarkness(BlockPos pos, WorldView world, CallbackInfoReturnable<Float> cir) {
		if (survivesSunlight) {
			cir.setReturnValue(0.0f);
		}
	}

	@Override
	public boolean metacraft_lib$survivesSunlight() {
		return survivesSunlight;
	}

	@Override
	public void metacraft_lib$setSurvivesSunlight(boolean survivesSunlight) {
		this.survivesSunlight = survivesSunlight;
	}
}

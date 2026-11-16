package se.metacraft.ovvar.clienttest.mixin;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.clienttest.StillArms;

/** While StillArms holds, a humanoid's arms skip their idle sway. Test source set only. */
@Mixin(AnimationUtils.class)
public abstract class ArmSwayMixin {
	@Inject(method = "bobModelPart", at = @At("HEAD"), cancellable = true)
	private static void ovvar$holdSway(ModelPart part, float ageInTicks, float scale, CallbackInfo ci) {
		if (StillArms.held()) ci.cancel();
	}
}

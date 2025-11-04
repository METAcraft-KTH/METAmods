package nu.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import nu.metacraft.portable_jukebox.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {

	@ModifyReturnValue(method = "canHoldAnyFluid", at = @At("RETURN"))
	private static boolean canHoldAnyFluid(boolean original, @Local(argsOnly = true) BlockState state) {
		if (state.is(Blocks.PORTABLE_JUKEBOX)) {
			return false;
		}
		return original;
	}

}

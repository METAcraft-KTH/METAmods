package se.metacraft.portable_jukebox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import se.metacraft.portable_jukebox.block.BoomboxBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {

	@ModifyReturnValue(method = "canHoldAnyFluid", at = @At("RETURN"))
	private static boolean canHoldAnyFluid(boolean original, @Local(argsOnly = true) BlockState state) {
		if (state.is(BoomboxBlocks.BOOMBOX)) {
			return false;
		}
		return original;
	}

}

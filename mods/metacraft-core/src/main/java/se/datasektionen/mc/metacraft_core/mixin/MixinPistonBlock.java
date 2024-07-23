package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_core.extensions.BlockEntityExtensions;

@Mixin(PistonBlock.class)
public class MixinPistonBlock {

	@Inject(method = "isMovable", at = @At("HEAD"), cancellable = true)
	private static void isMovable(
			BlockState state, World world, BlockPos pos, Direction direction, boolean canBreak,
			Direction pistonDir, CallbackInfoReturnable<Boolean> cir
	) {
		if (state.hasBlockEntity()) {
			var tile = world.getBlockEntity(pos);
			if (tile != null && !((BlockEntityExtensions) tile).metacraft_core$isMovable()) {
				cir.setReturnValue(false);
			}
		}
	}

}

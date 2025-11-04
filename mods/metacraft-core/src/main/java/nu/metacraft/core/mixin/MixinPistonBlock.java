package nu.metacraft.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import nu.metacraft.core.extensions.BlockEntityExtensions;

@Mixin(PistonBaseBlock.class)
public class MixinPistonBlock {

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private static void isMovable(
			BlockState state, Level world, BlockPos pos, Direction direction, boolean canBreak,
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

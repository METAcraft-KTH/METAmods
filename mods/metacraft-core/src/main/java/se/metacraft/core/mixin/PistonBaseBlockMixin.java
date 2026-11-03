package se.metacraft.core.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.core.extensions.BlockEntityExtensions;

@Mixin(value = PistonBaseBlock.class, priority = 1500)
public class PistonBaseBlockMixin {

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

	@Definition(id = "blockState", local = @Local(type = BlockState.class))
	@Definition(id = "getBlock", method = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;")
	@Definition(id = "GRINDSTONE", field = "Lnet/minecraft/world/level/block/Blocks;GRINDSTONE:Lnet/minecraft/world/level/block/Block;")
	@Expression("blockState.getBlock() == GRINDSTONE")
	@TargetHandler(name = "moveGrindstones", mixin = "carpet.mixins.PistonBaseBlock_movableBEMixin")
	@IfModLoaded("carpet")
	@ModifyExpressionValue(method = "@MixinSquared:Handler", at = @At("MIXINEXTRAS:EXPRESSION"))
	private static boolean fixGrindstoneErroneouslyPushable(boolean original) {
		return false;
	}

}

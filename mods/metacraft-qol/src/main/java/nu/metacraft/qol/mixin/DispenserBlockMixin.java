package nu.metacraft.qol.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import nu.metacraft.qol.void_anchor.block.VoidAnchorBlock;
import nu.metacraft.qol.void_anchor.block.VoidAnchorRefillBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DispenserBlock.class)
public class DispenserBlockMixin {

	@ModifyExpressionValue(
			method = "dispenseFrom",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/DispenserBlock;getDispenseMethod(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/dispenser/DispenseItemBehavior;"
			)
	)
	private DispenseItemBehavior metacraft_qol$refillVoidAnchor(
			DispenseItemBehavior fallback, @Local BlockSource source, @Local ItemStack stack
	) {
		var target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
		if (
				source.level().getBlockState(target).getBlock() instanceof VoidAnchorBlock
						&& VoidAnchorConfig.getInstance().enabled()
						&& VoidAnchorConfig.getInstance().isFuel(stack)
		) {
			return new VoidAnchorRefillBehaviour();
		}
		return fallback;
	}

}

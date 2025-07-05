package nu.metacraft.relay.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import nu.metacraft.relay.blocks.block.RelayBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DispenserBlock.class)
public class MixinDispenserBlock {

	@ModifyExpressionValue(
		method = "dispense",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/block/DispenserBlock;getBehaviorForItem(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/block/dispenser/DispenserBehavior;"
		)
	)
	public DispenserBehavior getBehaviorForItem(
			DispenserBehavior fallback, @Local BlockPointer pointer, @Local ItemStack itemStack
	) {
		Direction direction = pointer.state().get(DispenserBlock.FACING);
		BlockPos targetPos = pointer.pos().offset(direction);
		if (RelayBlock.isChargeItem(itemStack, pointer.world(), targetPos)) {
			return new RelayBlock.RefillBehaviour(fallback);
		}
		return fallback;
	}

}

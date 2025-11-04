package nu.metacraft.relay.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import nu.metacraft.relay.blocks.block.RelayBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DispenserBlock.class)
public class MixinDispenserBlock {

	@ModifyExpressionValue(
		method = "dispenseFrom",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/level/block/DispenserBlock;getDispenseMethod(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/dispenser/DispenseItemBehavior;"
		)
	)
	public DispenseItemBehavior getBehaviorForItem(
			DispenseItemBehavior fallback, @Local BlockSource pointer, @Local ItemStack itemStack
	) {
		Direction direction = pointer.state().getValue(DispenserBlock.FACING);
		BlockPos targetPos = pointer.pos().relative(direction);
		if (RelayBlock.isChargeItem(itemStack, pointer.level(), targetPos)) {
			return new RelayBlock.RefillBehaviour(fallback);
		}
		return fallback;
	}

}

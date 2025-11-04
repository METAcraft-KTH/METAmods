package nu.metacraft.simplecustomfeatures.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal.PortalBlockObject;

@Mixin(DispenserBlock.class)
public class MixinDispenserBlock {

	@ModifyReturnValue(method = "getDispenseMethod(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/dispenser/DispenseItemBehavior;", at = @At("RETURN"))
	protected DispenseItemBehavior getDispenserBehavior(
			DispenseItemBehavior original, @Local(argsOnly = true) Level world, @Local(argsOnly = true) ItemStack stack
	) {
		if (!(world instanceof ServerLevel sw)) return original;
		return PortalBlockObject.getForItem(stack, sw).map(portal -> portal.getDispenserBehaviour(original)).orElse(original);
	}

}

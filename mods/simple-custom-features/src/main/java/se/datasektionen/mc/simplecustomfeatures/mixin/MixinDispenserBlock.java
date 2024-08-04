package se.datasektionen.mc.simplecustomfeatures.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.simplecustomfeatures.objects.blocks.dynamic_portal.PortalBlockObject;

@Mixin(DispenserBlock.class)
public class MixinDispenserBlock {

	@ModifyReturnValue(method = "getBehaviorForItem", at = @At("RETURN"))
	protected DispenserBehavior getDispenserBehavior(
			DispenserBehavior original, @Local(argsOnly = true) World world, @Local(argsOnly = true) ItemStack stack
	) {
		if (!(world instanceof ServerWorld sw)) return original;
		return PortalBlockObject.getForItem(stack, sw).map(portal -> portal.getDispenserBehaviour(original)).orElse(original);
	}

}

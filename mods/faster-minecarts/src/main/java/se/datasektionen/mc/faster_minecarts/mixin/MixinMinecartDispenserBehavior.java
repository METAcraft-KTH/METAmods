package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.dispenser.MinecartDispenserBehavior;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;

@Mixin(MinecartDispenserBehavior.class)
public class MixinMinecartDispenserBehavior {

	@Inject(
		method = "dispenseSilently",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/world/ServerWorld;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
		)
	)
	public void onDispenseSilently(
			BlockPointer pointer, ItemStack stack, CallbackInfoReturnable<ItemStack> cir,
			@Local AbstractMinecartEntity abstractMinecartEntity
	) {
		FasterMinecarts.modifyEntityFromItem(stack, abstractMinecartEntity);
	}
}

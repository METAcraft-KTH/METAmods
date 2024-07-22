package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.MinecartItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPointer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.faster_minecarts.FasterMinecarts;

@Mixin(MinecartItem.class)
public class MixinMinecartItem {

	@Inject(
		method = "useOnBlock",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
		)
	)
	public void useOnBlock(
		ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir,
		@Local AbstractMinecartEntity abstractMinecartEntity
	) {
		FasterMinecarts.modifyEntityFromItem(context.getStack(), abstractMinecartEntity);
	}

	@Mixin(targets = "net.minecraft.item.MinecartItem$1")
	public static class Dispenser {
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

}

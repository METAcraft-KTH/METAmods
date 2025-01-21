package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.faster_minecarts.FasterMinecartsHelper;

@Mixin(ShapeContext.class)
public interface MixinShapeContext {

	@ModifyExpressionValue(
		method = "of(Lnet/minecraft/entity/Entity;)Lnet/minecraft/block/ShapeContext;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;areMinecartImprovementsEnabled(Lnet/minecraft/world/World;)Z"
		)
	)
	private static boolean checkIfCart(boolean original, @Local AbstractMinecartEntity minecart) {
		return FasterMinecartsHelper.areMinecartExperimentsEnabledForCart(original, minecart);
	}

}

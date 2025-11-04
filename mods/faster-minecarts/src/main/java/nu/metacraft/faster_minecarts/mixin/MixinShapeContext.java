package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.faster_minecarts.FasterMinecartsHelper;

@Mixin(CollisionContext.class)
public interface MixinShapeContext {

	@ModifyExpressionValue(
		method = "of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;useExperimentalMovement(Lnet/minecraft/world/level/Level;)Z"
		)
	)
	private static boolean checkIfCart(boolean original, @Local AbstractMinecart minecart) {
		return FasterMinecartsHelper.areMinecartExperimentsEnabledForCart(original, minecart);
	}

}

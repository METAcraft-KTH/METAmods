package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.faster_minecarts.FasterMinecartsHelper;
import nu.metacraft.faster_minecarts.MinecartComponents;

@Mixin(MinecartFurnace.class)
public abstract class MixinFurnaceMinecart extends AbstractMinecart {

	protected MixinFurnaceMinecart(EntityType<?> entityType, Level world) {
		super(entityType, world);
	}

	@ModifyReturnValue(method = "getMaxSpeed", at = @At("RETURN"))
	public double getMaxSpeed(double maxSpeed) {
		return FasterMinecartsHelper.getActualMaxSpeed(this, maxSpeed);
	}

	@ModifyExpressionValue(
			method = "applyNaturalSlowdown",
			at = @At(
					value = "CONSTANT",
					args = "doubleValue=0.1"
			)
	)
	public double changeUnderwaterApplySlowdown(double original) {
		return FasterMinecartsHelper.getMinecartItem(this).map(
				item -> item.get(MinecartComponents.UNDERWATER_SLOWDOWN)
		).orElse(original);
	}

	@ModifyExpressionValue(
			method = "applyNaturalSlowdown",
			at = @At(
					value = "CONSTANT",
					args = "doubleValue=0.98"
			)
	)
	public double changeNormalSlowdown(double original) {
		return FasterMinecartsHelper.getMinecartItem(this).map(
				item -> item.get(MinecartComponents.SLOWDOWN)
		).orElse(original);
	}

}

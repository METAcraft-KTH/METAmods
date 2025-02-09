package se.datasektionen.mc.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.faster_minecarts.FasterMinecartsHelper;
import se.datasektionen.mc.faster_minecarts.MinecartComponents;

@Mixin(FurnaceMinecartEntity.class)
public abstract class MixinFurnaceMinecart extends AbstractMinecartEntity {

	protected MixinFurnaceMinecart(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@ModifyReturnValue(method = "getMaxSpeed", at = @At("RETURN"))
	public double getMaxSpeed(double maxSpeed) {
		return FasterMinecartsHelper.getActualMaxSpeed(this, maxSpeed);
	}

	@ModifyExpressionValue(
			method = "applySlowdown",
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
			method = "applySlowdown",
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

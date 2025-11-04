package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.faster_minecarts.FasterMinecartsHelper;
import nu.metacraft.faster_minecarts.MinecartComponents;

@Mixin(AbstractMinecartContainer.class)
public abstract class AbstractMinecartContainerMixin extends AbstractMinecart {

	protected AbstractMinecartContainerMixin(EntityType<?> entityType, Level world) {
		super(entityType, world);
	}

	@ModifyExpressionValue(
			method = "applyNaturalSlowdown",
			at = @At(
					value = "CONSTANT",
					args = "floatValue=0.95"
			)
	)
	public float changeUnderwaterApplySlowdown(float original) {
		var f = FasterMinecartsHelper.getMinecartItem(this).map(
				item -> item.get(MinecartComponents.UNDERWATER_SLOWDOWN)
		);
		if (f.isPresent()) return f.orElseThrow().floatValue();
		return original;
	}

	@ModifyExpressionValue(
			method = "applyNaturalSlowdown",
			at = @At(
					value = "CONSTANT",
					args = "floatValue=0.98"
			)
	)
	public float changeNormalSlowdown(float original) {
		var f = FasterMinecartsHelper.getMinecartItem(this).map(
				item -> item.get(MinecartComponents.SLOWDOWN)
		);
		if (f.isPresent()) return f.orElseThrow().floatValue();
		return original;
	}

	@ModifyExpressionValue(
			method = "applyNaturalSlowdown",
			at = @At(
					value = "CONSTANT",
					args = "floatValue=0.001"
			)
	)
	public float changeItemSlowdownModifier(float original) {
		return FasterMinecartsHelper.getMinecartItem(this).map(
				item -> item.get(MinecartComponents.ITEM_SLOWDOWN_MODIFIER)
		).orElse(original);
	}

}

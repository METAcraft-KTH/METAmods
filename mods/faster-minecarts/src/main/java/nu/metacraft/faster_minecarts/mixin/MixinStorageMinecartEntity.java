package nu.metacraft.faster_minecarts.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.faster_minecarts.FasterMinecartsHelper;
import nu.metacraft.faster_minecarts.MinecartComponents;

@Mixin(StorageMinecartEntity.class)
public abstract class MixinStorageMinecartEntity extends AbstractMinecartEntity {

	protected MixinStorageMinecartEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@ModifyExpressionValue(
			method = "applySlowdown",
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
			method = "applySlowdown",
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
			method = "applySlowdown",
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

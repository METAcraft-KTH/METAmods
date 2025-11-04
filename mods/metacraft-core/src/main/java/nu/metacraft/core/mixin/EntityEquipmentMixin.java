package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.core.item.ItemModifiers;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

@Mixin(EntityEquipment.class)
public class EntityEquipmentMixin {

	@ModifyExpressionValue(
		method = "tick",
		at = @At(
				value = "INVOKE",
				target = "Ljava/util/Map$Entry;getValue()Ljava/lang/Object;"
		)
	)
	public <V> V tick(
			V original, @Local(argsOnly = true) Entity entity,
			@Local Map.Entry<EquipmentSlot, ItemStack> entry
	) {
		return ItemModifiers.modifyTick(
				(ItemStack) original, entity.getRandom()
		).map(result -> {
			entry.setValue(result);
			return (V) result;
		}).orElse(original);
	}

}

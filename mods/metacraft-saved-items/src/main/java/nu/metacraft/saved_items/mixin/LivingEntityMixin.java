package nu.metacraft.saved_items.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Shadow
	public abstract boolean isDeadOrDying();

	@WrapMethod(method = "createItemStackToDrop")
	public @Nullable ItemEntity createItemStackToDrop(
			ItemStack itemStack, boolean randomly, boolean thrownFromHand, Operation<ItemEntity> original
	) {
		return original.call(itemStack, randomly, thrownFromHand);
	}
}

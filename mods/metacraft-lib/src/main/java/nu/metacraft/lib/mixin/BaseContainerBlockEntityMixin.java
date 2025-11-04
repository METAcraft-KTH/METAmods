package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import nu.metacraft.lib.extensions.AbstractFurnaceEntityExtensions;

@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin {

	@Shadow public abstract ItemStack getItem(int slot);

	@Inject(
			method = {
					"removeItem(II)Lnet/minecraft/world/item/ItemStack;"
			},
			at = @At("RETURN")
	)
	public void onRemoveStack(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof AbstractFurnaceBlockEntity && this.getItem(slot).isEmpty()) {
			((AbstractFurnaceEntityExtensions) this).metacraft_lib$unsetInputExtractable();
		}
	}

	@Inject(
			method = {
					"removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;"
			},
			at = @At("RETURN")
	)
	public void onRemoveStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof AbstractFurnaceBlockEntity) {
			((AbstractFurnaceEntityExtensions) this).metacraft_lib$unsetInputExtractable();
		}
	}

}

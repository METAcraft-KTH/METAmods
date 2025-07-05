package nu.metacraft.lib.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.extensions.AbstractFurnaceEntityExtensions;

@Mixin(LockableContainerBlockEntity.class)
public abstract class MixinLockableContainerBlockEntity {

	@Shadow public abstract ItemStack getStack(int slot);

	@Inject(
			method = {
					"removeStack(II)Lnet/minecraft/item/ItemStack;"
			},
			at = @At("RETURN")
	)
	public void onRemoveStack(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof AbstractFurnaceBlockEntity && this.getStack(slot).isEmpty()) {
			((AbstractFurnaceEntityExtensions) this).metacraft_lib$unsetInputExtractable();
		}
	}

	@Inject(
			method = {
					"removeStack(I)Lnet/minecraft/item/ItemStack;"
			},
			at = @At("RETURN")
	)
	public void onRemoveStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof AbstractFurnaceBlockEntity) {
			((AbstractFurnaceEntityExtensions) this).metacraft_lib$unsetInputExtractable();
		}
	}

}

package se.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import se.metacraft.portable_jukebox.entity.BoomboxEntity;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

	@Inject(method = "setCarried", at = @At("HEAD"))
	public void setCursorStack(ItemStack stack, CallbackInfo ci) {
		BoomboxEntity.transferInScreenHandlerFromUnknown(
				stack, ((AbstractContainerMenu) (Object) this)
		);
	}

}

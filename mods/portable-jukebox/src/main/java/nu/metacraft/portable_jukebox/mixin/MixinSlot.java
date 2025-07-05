package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(Slot.class)
public class MixinSlot {

	@Shadow @Final public Inventory inventory;

	@Inject(method = "setStackNoCallbacks", at = @At("HEAD"))
	public void setStackNoCallbacks(ItemStack stack, CallbackInfo ci) {
		PortableJukeboxEntity.transferToInventoryFromUnknown(
				stack, inventory
		);
	}

}

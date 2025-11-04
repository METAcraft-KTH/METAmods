package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(Slot.class)
public class MixinSlot {

	@Shadow @Final public Container container;

	@Inject(method = "set", at = @At("HEAD"))
	public void setStackNoCallbacks(ItemStack stack, CallbackInfo ci) {
		PortableJukeboxEntity.transferToInventoryFromUnknown(
				stack, container
		);
	}

}

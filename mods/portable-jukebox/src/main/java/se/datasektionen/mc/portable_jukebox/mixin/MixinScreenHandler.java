package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ScreenHandler.class)
public class MixinScreenHandler {

	@Inject(method = "setCursorStack", at = @At("HEAD"))
	public void setCursorStack(ItemStack stack, CallbackInfo ci) {
        PortableJukeboxEntity.transferInScreenHandlerFromUnknown(
				stack, ((ScreenHandler) (Object) this)
        );
    }

}

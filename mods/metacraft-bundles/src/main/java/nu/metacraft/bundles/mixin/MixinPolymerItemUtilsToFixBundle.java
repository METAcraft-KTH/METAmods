package nu.metacraft.bundles.mixin;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.bundles.BundleConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(PolymerItemUtils.class)
public class MixinPolymerItemUtilsToFixBundle {

	@Inject(
		method = "isPolymerServerItem(Lnet/minecraft/world/item/ItemStack;Lxyz/nucleoid/packettweaker/PacketContext;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void isPolymerServerItem(
			ItemStack itemStack, PacketContext context, CallbackInfoReturnable<Boolean> cir
	) {
		if (itemStack.has(DataComponents.BUNDLE_CONTENTS) && BundleConfig.getInstance().bundleRendering()) {
			cir.setReturnValue(true);
		}
	}

}

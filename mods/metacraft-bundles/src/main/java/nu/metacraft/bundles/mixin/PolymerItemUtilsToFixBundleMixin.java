package nu.metacraft.bundles.mixin;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.bundles.BundleConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PolymerItemUtils.class)
public class PolymerItemUtilsToFixBundleMixin {

	@Inject(
		method = "isPolymerServerItem(Lnet/minecraft/world/item/ItemInstance;Lnet/fabricmc/fabric/api/networking/v1/context/PacketContext;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void isPolymerServerItem(
			ItemInstance itemInstance, PacketContext context, CallbackInfoReturnable<Boolean> cir
	) {
		if (itemInstance.get(DataComponents.BUNDLE_CONTENTS) != null && BundleConfig.getInstance().bundleRendering()) {
			cir.setReturnValue(true);
		}
	}

}

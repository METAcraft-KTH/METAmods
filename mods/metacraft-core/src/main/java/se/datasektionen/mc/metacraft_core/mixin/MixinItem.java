package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BundleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.util.helper.BundleHelper;

@Mixin(Item.class)
public class MixinItem {

	@Inject(method = "postProcessComponents", at = @At("RETURN"))
	public void postProcessComponents(ItemStack stack, CallbackInfo ci) {
		if (Thread.currentThread().getName().contains("Netty")) {
			return; //Prevent bundles from updating size from the packet deserialiser, because that breaks the text updater completely.
		}
		if ((Object) this instanceof BundleItem && stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
			BundleHelper.fixBundle(stack);
		}
	}

}

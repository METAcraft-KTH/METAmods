package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import nu.metacraft.bundles.BundleComponents;
import nu.metacraft.bundles.util.BundleHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public class MixinItem {

	@Mixin(Item.Settings.class)
	public static class Settings {

		@ModifyReturnValue(
			method = "getValidatedComponents",
			at = @At("RETURN")
		)
		public ComponentMap getValidatedComponents(ComponentMap components) {
			if (components.contains(BundleComponents.BUNDLE_SIZE_FACTOR) && components.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
				return BundleHelper.fixBundle(components);
			}
			return components;
		}
	}

}

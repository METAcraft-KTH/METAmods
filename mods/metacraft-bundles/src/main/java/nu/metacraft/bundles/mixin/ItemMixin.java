package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import nu.metacraft.bundles.BundleComponents;
import nu.metacraft.bundles.util.BundleHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public class ItemMixin {

	@Mixin(Item.Properties.class)
	public static class Properties {

		@ModifyReturnValue(
			method = "buildAndValidateComponents",
			at = @At("RETURN")
		)
		public DataComponentMap getValidatedComponents(DataComponentMap components) {
			if (components.has(BundleComponents.BUNDLE_SIZE_FACTOR) && components.has(DataComponents.BUNDLE_CONTENTS)) {
				return BundleHelper.fixBundle(components);
			}
			return components;
		}
	}

}

package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.component.DataComponentInitializers;
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
			method = "finalizeInitializer",
			at = @At("RETURN")
		)
		public DataComponentInitializers.Initializer<Item> getValidatedComponents(DataComponentInitializers.Initializer<Item> original) {
			return original.andThen((components, context, key) -> {
				if (components.contains(BundleComponents.BUNDLE_SIZE_FACTOR) && components.contains(DataComponents.BUNDLE_CONTENTS)) {
					BundleHelper.fixBundle(components);
				}
			});
		}
	}

}

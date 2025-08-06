package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import nu.metacraft.bundles.util.BundleHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemStack.class)
public class MixinItemStack {

	@Shadow @Final MergedComponentMap components;

	@ModifyArg(
			method = "set",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/component/MergedComponentMap;set(Lnet/minecraft/component/ComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
			),
			index = 1
	)
	public <T> T set(
			@Nullable T value, @Local(argsOnly = true) ComponentType<? super T> type
	) {//When we "clear" the bundle, we need to copy the bundle size factor.
		if (type == DataComponentTypes.BUNDLE_CONTENTS && value == BundleContentsComponent.DEFAULT) {
			var existing = (BundleContentsComponent) components.get(type);
			if (existing != null) {
				return (T) BundleHelper.fixBundle((BundleContentsComponent) value, (ItemStack) (Object) this);
			}
		}
		return value;
	}

}

package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.*;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import nu.metacraft.bundles.util.BundleHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

	@Inject(method = "<init>(Lnet/minecraft/item/ItemConvertible;ILnet/minecraft/component/MergedComponentMap;)V", at = @At("RETURN"))
	public void init(ItemConvertible item, int count, MergedComponentMap components, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

	@Inject(method = "applyChanges", at = @At("RETURN"))
	public void applyChanges(ComponentChanges changes, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

	@Inject(method = "applyUnvalidatedChanges", at = @At("RETURN"))
	public void applyUnvalidatedChanges(ComponentChanges changes, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

	@Inject(method = "applyComponentsFrom", at = @At("RETURN"))
	public void applyComponentsFrom(ComponentMap components, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

}

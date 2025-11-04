package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.ItemLike;
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

	@Shadow @Final PatchedDataComponentMap components;

	@ModifyArg(
			method = "set",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/component/PatchedDataComponentMap;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
			),
			index = 1
	)
	public <T> T set(
			@Nullable T value, @Local(argsOnly = true) DataComponentType<? super T> type
	) {//When we "clear" the bundle, we need to copy the bundle size factor.
		if (type == DataComponents.BUNDLE_CONTENTS && value == BundleContents.EMPTY) {
			var existing = (BundleContents) components.get(type);
			if (existing != null) {
				return (T) BundleHelper.fixBundle((BundleContents) value, (ItemStack) (Object) this);
			}
		}
		return value;
	}

	@Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("RETURN"))
	public void init(ItemLike item, int count, PatchedDataComponentMap components, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

	@Inject(method = "applyComponentsAndValidate", at = @At("RETURN"))
	public void applyChanges(DataComponentPatch changes, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

	@Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V", at = @At("RETURN"))
	public void applyUnvalidatedChanges(DataComponentPatch changes, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

	@Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V", at = @At("RETURN"))
	public void applyComponentsFrom(DataComponentMap components, CallbackInfo ci) {
		BundleHelper.fixBundle((ItemStack) (Object) this);
	}

}

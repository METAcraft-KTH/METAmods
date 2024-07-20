package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.extensions.BundlesComponentExtensions;
import se.datasektionen.mc.metacraft_lib.util.helper.BundleHelper;

import java.util.List;

@Mixin(BundleContentsComponent.class)
public abstract class MixinBundleContentsComponent implements BundlesComponentExtensions.Internal {

	@Unique
	private Fraction bundleSizeFactor = Fraction.ONE;

	@ModifyExpressionValue(
		method = "getOccupancy(Lnet/minecraft/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/component/type/BundleContentsComponent;getOccupancy()Lorg/apache/commons/lang3/math/Fraction;"
		)
	)
	private static Fraction getOccupancy(Fraction fraction, @Local BundleContentsComponent bundle) {
		return fraction.multiplyBy(BundleHelper.getStoredBundleSizeFactor(bundle));
	}

	@Override
	public void METAcraft_Fixes$setBundleSizeFactor(Fraction factor) {
		this.bundleSizeFactor = factor;
	}

	@Override
	public Fraction METAcraft_Fixes$getBundleSizeFactor() {
		return bundleSizeFactor;
	}

	@ModifyExpressionValue(
		method = "equals",
		at = @At(
			value = "INVOKE",
			target = "Lorg/apache/commons/lang3/math/Fraction;equals(Ljava/lang/Object;)Z"
		)
	)
	public boolean equals(
			boolean original,
			@Local(ordinal = 1) BundleContentsComponent bundleContentsComponent
	) {
		return original && this.bundleSizeFactor.equals(BundleHelper.getStoredBundleSizeFactor(bundleContentsComponent));
	}

	@Mixin(BundleContentsComponent.Builder.class)
	public static class Builder implements Internal {
		@Shadow private Fraction occupancy;
		@Shadow @Final private List<ItemStack> stacks;
		@Unique
		private Fraction bundleSizeFactor;

		@Inject(method = "<init>", at = @At("RETURN"))
		public void init(BundleContentsComponent base, CallbackInfo ci) {
			this.bundleSizeFactor = ((BundlesComponentExtensions) (Object) base).METAcraft_Fixes$getBundleSizeFactor();
		}

		@ModifyExpressionValue(
			method = {"getMaxAllowed", "add(Lnet/minecraft/item/ItemStack;)I", "removeFirst"},
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/component/type/BundleContentsComponent;getOccupancy(Lnet/minecraft/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;"
			)
		)
		public Fraction fixGetOccupancy(Fraction original) {
			return original.divideBy(bundleSizeFactor);
		}

		@ModifyReturnValue(method = "build", at = @At("RETURN"))
		public BundleContentsComponent build(BundleContentsComponent original) {
			((BundlesComponentExtensions.Internal) (Object) original).METAcraft_Fixes$setBundleSizeFactor(bundleSizeFactor);
			return original;
		}

		@Override
		public void METAcraft_Fixes$setBundleSizeFactor(Fraction factor) {
			bundleSizeFactor = factor;
			occupancy = AccessorBundleContentsComponent.callCalculateOccupancy(this.stacks).divideBy(factor);
		}

		@Override
		public Fraction METAcraft_Fixes$getBundleSizeFactor() {
			return bundleSizeFactor;
		}
	}
}

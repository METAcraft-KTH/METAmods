package nu.metacraft.bundles.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import nu.metacraft.bundles.extensions.BundlesComponentExtensions;
import nu.metacraft.bundles.util.BundleHelper;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

@Mixin(BundleContents.class)
public abstract class MixinBundleContentsComponent implements BundlesComponentExtensions.Internal {

	@Unique
	private Fraction bundleSizeFactor = Fraction.ONE;

	@ModifyExpressionValue(
		method = "getWeight(Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/component/BundleContents;weight()Lorg/apache/commons/lang3/math/Fraction;"
		)
	)
	private static Fraction getOccupancy(Fraction fraction, @Local BundleContents bundle) {
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
			@Local BundleContents bundleContentsComponent
	) {
		return original && this.bundleSizeFactor.equals(BundleHelper.getStoredBundleSizeFactor(bundleContentsComponent));
	}

	@Mixin(BundleContents.Mutable.class)
	public static abstract class Builder implements Internal {
		@Shadow private Fraction weight;
		@Shadow @Final private List<ItemStack> items;

		@Shadow public abstract int tryInsert(ItemStack stack);

		@Unique
		private Fraction bundleSizeFactor;

		@Inject(method = "<init>", at = @At("RETURN"))
		public void init(BundleContents base, CallbackInfo ci) {
			this.bundleSizeFactor = ((BundlesComponentExtensions) (Object) base).METAcraft_Fixes$getBundleSizeFactor();
		}

		@ModifyExpressionValue(
			method = {"getMaxAmountToAdd", "tryInsert(Lnet/minecraft/world/item/ItemStack;)I", "removeOne"},
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/component/BundleContents;getWeight(Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;"
			)
		)
		public Fraction fixGetOccupancy(Fraction original) {
			return original.divideBy(bundleSizeFactor);
		}

		@ModifyReturnValue(method = "toImmutable", at = @At("RETURN"))
		public BundleContents build(BundleContents original) {
			((BundlesComponentExtensions.Internal) (Object) original).METAcraft_Fixes$setBundleSizeFactor(bundleSizeFactor);
			return original;
		}

		@Override
		public void METAcraft_Fixes$setBundleSizeFactor(Fraction factor) {
			bundleSizeFactor = factor;
			weight = AccessorBundleContentsComponent.callComputeContentWeight(this.items).divideBy(factor);
		}

		@Override
		public Fraction METAcraft_Fixes$getBundleSizeFactor() {
			return bundleSizeFactor;
		}

		@ModifyExpressionValue(
			method = "findStackIndex",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
			)
		)
		public boolean getInsertionIndex(boolean original, @Local int i) {
			var s = this.items.get(i);
			if (s.getCount() >= s.getMaxStackSize()) {
				return false;
			}
			return original;
		}

		@ModifyArg(
			method = "tryInsert(Lnet/minecraft/world/item/ItemStack;)I",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"
			)
		)
		public int add(
				int total, @Local(ordinal = 1) ItemStack itemStack
		) {
			if (total > itemStack.getMaxStackSize()) {
				items.addFirst(itemStack.copyWithCount(itemStack.getMaxStackSize()));
				return total - itemStack.getMaxStackSize();
			}
			return total;
		}

	}
}

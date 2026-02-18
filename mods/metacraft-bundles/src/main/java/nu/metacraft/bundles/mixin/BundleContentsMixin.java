package nu.metacraft.bundles.mixin;

import com.google.common.base.Suppliers;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DataResult;
import nu.metacraft.bundles.METAcraftBundles;
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
public abstract class BundleContentsMixin implements BundlesComponentExtensions.Internal {

	@Unique
	private Fraction bundleSizeFactor = Fraction.ONE;

	@ModifyExpressionValue(
			method = "getWeight",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/component/BundleContents;weight()Lcom/mojang/serialization/DataResult;"
			)
	)
	private static DataResult<Fraction> getOccupancy(
			DataResult<Fraction> original, @Local(name = "bundle") BundleContents bundle
	) {
		return original.flatMap(
				fraction -> BundleHelper.runSafeFractionOperation(() -> fraction.multiplyBy(BundleHelper.getStoredBundleSizeFactor(bundle)))
		);
	}

	@Override
	public void metacraft_bundles$setBundleSizeFactor(Fraction factor) {
		this.bundleSizeFactor = factor;
	}

	@Override
	public Fraction metacraft_bundles$getBundleSizeFactor() {
		return bundleSizeFactor;
	}

	@ModifyExpressionValue(
		method = "equals",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/List;equals(Ljava/lang/Object;)Z"
		)
	)
	public boolean equals(
			boolean original,
			@Local(name = "contents") BundleContents contents
	) {
		return original && this.bundleSizeFactor.equals(BundleHelper.getStoredBundleSizeFactor(contents));
	}

	@Mixin(BundleContents.Mutable.class)
	public static abstract class Mutable implements Internal {
		@Shadow private Fraction weight;
		@Shadow @Final private List<ItemStack> items;

		@Unique
		private Fraction bundleSizeFactor;

		@Inject(method = "<init>", at = @At("RETURN"))
		public void init(BundleContents base, CallbackInfo ci) {
			this.bundleSizeFactor = ((BundlesComponentExtensions) (Object) base).metacraft_bundles$getBundleSizeFactor();
		}

		@ModifyExpressionValue(
			method = {"getMaxAmountToAdd", "tryInsert(Lnet/minecraft/world/item/ItemStack;)I", "removeOne"},
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/component/BundleContents;getWeight(Lnet/minecraft/world/item/ItemInstance;)Lcom/mojang/serialization/DataResult;"
			)
		)
		public DataResult<Fraction> fixGetOccupancy(DataResult<Fraction> original) {
			return original.flatMap(fraction -> BundleHelper.runSafeFractionOperation(() -> fraction.divideBy(bundleSizeFactor)));
		}

		@ModifyReturnValue(method = "toImmutable", at = @At("RETURN"))
		public BundleContents build(BundleContents original) {
			((BundlesComponentExtensions.Internal) (Object) original).metacraft_bundles$setBundleSizeFactor(bundleSizeFactor);
			((BundleContentsAccessor) (Object) original).setWeight(
					Suppliers.memoize(
							() -> BundleContentsAccessor.callComputeContentWeight(items).flatMap(
									fraction -> BundleHelper.runSafeFractionOperation(() -> fraction.divideBy(bundleSizeFactor))
							)
					)
			);
			return original;
		}

		@Override
		public void metacraft_bundles$setBundleSizeFactor(Fraction factor) {
			bundleSizeFactor = factor;
			weight = BundleContentsAccessor.callComputeContentWeight(this.items).flatMap(
					fraction -> BundleHelper.runSafeFractionOperation(() -> fraction.divideBy(factor))
			).resultOrPartial(METAcraftBundles.LOGGER::error).orElse(
					Fraction.ZERO
			);
		}

		@Override
		public Fraction metacraft_bundles$getBundleSizeFactor() {
			return bundleSizeFactor;
		}

		@ModifyExpressionValue(
			method = "findStackIndex",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
			)
		)
		public boolean getInsertionIndex(boolean original, @Local(name = "i") int i) {
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
				int total, @Local(name = "removedStack") ItemStack removedStack
		) {
			if (total > removedStack.getMaxStackSize()) {
				items.addFirst(removedStack.copyWithCount(removedStack.getMaxStackSize()));
				return total - removedStack.getMaxStackSize();
			}
			return total;
		}

	}
}

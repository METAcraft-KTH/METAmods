package se.metacraft.bundles.mixin;

import com.google.common.base.Suppliers;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DataResult;
import net.minecraft.world.item.component.GrowableMutableContainer;
import se.metacraft.bundles.METAcraftBundles;
import se.metacraft.bundles.extensions.BundlesComponentExtensions;
import se.metacraft.bundles.util.BundleHelper;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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

	@ModifyExpressionValue(
			method = {
					"asMutable()Lnet/minecraft/world/item/component/BundleContents$Mutable;",
					"copyWithContents(Ljava/util/stream/Stream;)Lnet/minecraft/world/item/component/BundleContents;"
			},
			at = {
					@At(
							value = "NEW",
							target = "()Lnet/minecraft/world/item/component/BundleContents$Mutable;"
					),
					@At(
							value = "NEW",
							target = "(Ljava/util/List;Lorg/apache/commons/lang3/math/Fraction;I)Lnet/minecraft/world/item/component/BundleContents$Mutable;"
					)
			},
			require = 3
	)
	private BundleContents.Mutable toMutable(BundleContents.Mutable original) {
		((BundlesComponentExtensions.Internal) original).metacraft_bundles$setBundleSizeFactor(bundleSizeFactor);
		return original;
	}

	@Mixin(BundleContents.Mutable.class)
	public static abstract class Mutable extends GrowableMutableContainer<BundleContents> implements Internal {
		@Shadow private Fraction weight;

		@Unique
		private Fraction bundleSizeFactor;

		public Mutable(List<ItemStack> items) {
			super(items);
		}

		@ModifyExpressionValue(
			method = {"tryInsert(Lnet/minecraft/world/item/ItemStack;)I", "tryTransfer"},
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/component/BundleContents;getWeight(Lnet/minecraft/world/item/ItemInstance;)Lcom/mojang/serialization/DataResult;"
			)
		)
		public DataResult<Fraction> fixGetOccupancy(DataResult<Fraction> original) {
			return original.flatMap(fraction -> BundleHelper.runSafeFractionOperation(() -> fraction.divideBy(bundleSizeFactor)));
		}

		/** What taking a stack out (or replacing one) frees: its weight in this bundle, scaled by the factor like the rest. */
		@WrapOperation(
			method = {"removeOne", "setItem"},
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/component/BundleContents$Mutable;getStackedWeight(Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;"
			),
			require = 2
		)
		private Fraction scaleRemovedWeight(ItemStack stack, Operation<Fraction> original) {
			return original.call(stack).divideBy(bundleSizeFactor);
		}

		/**
		 * The weight after a stack is put in a slot: vanilla adds the stack's weight at factor 1 and refuses
		 * past full; here its share is scaled by the factor, and full is still 1. One item asks vanilla whether
		 * it can go in at all, and what it weighs.
		 */
		@WrapOperation(
			method = {"setItem", "addSlotWithItem"},
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/item/component/BundleContents$Mutable;getWeightWithAddedItems(Lorg/apache/commons/lang3/math/Fraction;Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;"
			),
			require = 2
		)
		private Fraction scaleAddedWeight(Fraction weight, ItemStack stack, Operation<Fraction> original) {
			Fraction one = original.call(Fraction.ZERO, stack.copyWithCount(1));
			if (one == null) return null;
			Fraction total = weight.add(one.multiplyBy(Fraction.getFraction(stack.getCount(), 1)).divideBy(bundleSizeFactor));
			return total.compareTo(Fraction.ONE) <= 0 ? total : null;
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
			method = "findStackIndexWithinRange",
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

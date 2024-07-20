package se.datasektionen.mc.metacraft_lib.util.helper;

import com.mojang.serialization.Codec;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.math.Fraction;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.extensions.BundlesComponentExtensions;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorBundleItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BundleHelper {

	public static final String MAX_STORAGE_KEY = "MaxStorage";

	public static final int VANILLA_DEFAULT = 64;

	public static void updateBundleSizeParameter(ItemStack bundle, Fraction occupancy) {
		var maxStorage = getMaxStorage(bundle);
		if (maxStorage != VANILLA_DEFAULT) {
			Text text = Text.translatable(
					"item.minecraft.bundle.fullness", MathHelper.multiplyFraction(occupancy, maxStorage), maxStorage
			).formatted(Formatting.GRAY).styled(style -> style.withItalic(false));

			if (bundle.contains(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP)) {
				bundle.remove(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP);
			}
			var lore = bundle.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT);
			for (int i = 0; i < lore.lines().size(); i++) {
				var line = lore.lines().get(i);
				if (
						line.getContent() instanceof TranslatableTextContent translatable &&
								translatable.getKey().equals("item.minecraft.bundle.fullness")
				) {
					if (text.equals(lore.lines().get(i))) return;
					List<Text> lines = new ArrayList<>(lore.lines());
					lines.set(i, text);
					lore = new LoreComponent(lines);
					bundle.set(DataComponentTypes.LORE, lore);
					return;
				}
			}
			bundle.set(DataComponentTypes.LORE, lore.with(text));
		}
	}

	public static Fraction getBundleSizeFactor(ItemStack stack) {
		return Fraction.getFraction(BundleHelper.getMaxStorage(stack), BundleHelper.VANILLA_DEFAULT);
	}

	private static BundleContentsComponent fixBundleInternal(BundleContentsComponent bundle, Fraction factor) {
		return BundleHelper.setBundleSizeFactor(
				new BundleContentsComponent.Builder(bundle), factor
		).build();
	}

	public static BundleContentsComponent fixBundle(BundleContentsComponent bundle, ItemStack stack) {
		return fixBundle(bundle, getBundleSizeFactor(stack));
	}

	public static BundleContentsComponent fixBundle(BundleContentsComponent bundle, Fraction factor) {
		if (bundle != null && !BundleHelper.getStoredBundleSizeFactor(bundle).equals(factor)) {
			return fixBundleInternal(bundle, factor);
		}
		return bundle;
	}

	public static void fixBundle(ItemStack stack) {
		var factor = getBundleSizeFactor(stack);
		var bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
		if (bundle != null && !BundleHelper.getStoredBundleSizeFactor(bundle).equals(factor)) {
			stack.set(
					DataComponentTypes.BUNDLE_CONTENTS,
					fixBundleInternal(bundle, factor)
			);
		}
	}

	public static void updateBundleSizeParameter(ItemStack bundle) {
		var contents = bundle.get(DataComponentTypes.BUNDLE_CONTENTS);
		if (contents == null) return;
		updateBundleSizeParameter(bundle, contents.getOccupancy());
	}

	public static int getMaxStorage(ItemStack bundle) {
		return Optional.ofNullable(bundle.get(DataComponentTypes.CUSTOM_DATA)).filter(
				nbt -> nbt.contains(MAX_STORAGE_KEY)
		).flatMap(
				nbt -> nbt.get(Codec.INT.optionalFieldOf(MAX_STORAGE_KEY)).resultOrPartial(
						METAcraftLib.LOGGER::error
				).orElse(Optional.empty())
		).orElse(AccessorBundleItem.getBundleMaxSize());
	}

	public static BundleContentsComponent.Builder setBundleSizeFactor(
			BundleContentsComponent.Builder builder, Fraction fraction
	) {
		((BundlesComponentExtensions.Internal) builder).METAcraft_Fixes$setBundleSizeFactor(fraction);
		return builder;
	}

	public static Fraction getStoredBundleSizeFactor(BundleContentsComponent bundle) {
		return ((BundlesComponentExtensions) (Object) bundle).METAcraft_Fixes$getBundleSizeFactor();
	}

	public static Fraction getStoredBundleSizeFactor(BundleContentsComponent.Builder bundle) {
		return ((BundlesComponentExtensions) bundle).METAcraft_Fixes$getBundleSizeFactor();
	}

}

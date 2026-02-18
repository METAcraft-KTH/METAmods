package nu.metacraft.bundles.util;

import com.mojang.serialization.DataResult;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;
import nu.metacraft.bundles.BundleComponents;
import nu.metacraft.bundles.BundleConfig;
import nu.metacraft.bundles.METAcraftBundles;
import nu.metacraft.bundles.extensions.BundlesComponentExtensions;
import org.apache.commons.lang3.math.Fraction;

import java.util.function.Supplier;

public class BundleHelper {

	private static final int MAX_PIXELS = 94;
	private static final FontDescription.Resource TEXTURES_FONT = new FontDescription.Resource(METAcraftBundles.getID("bundle_textures"));

	public static Component getOccupancyText(ItemStack bundle) {
		var contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
		if (contents == null) return null;
		return getOccupancyText(contents.weight());
	}

	public static DataResult<Fraction> runSafeFractionOperation(Supplier<Fraction> modifier) {
		try {
			return DataResult.success(modifier.get());
		} catch (ArithmeticException e) {
			return DataResult.error(() -> "Excessive total bundle weight");
		}
	}

	public static Component getOccupancyText(DataResult<Fraction> occupancy) {
		return occupancy.map(fraction -> {
			int pixelsToShow = Mth.mulAndTruncate(fraction, MAX_PIXELS);
			String text;
			if (MAX_PIXELS <= pixelsToShow) {
				text = "baf";
			} else {
				StringBuilder builder = new StringBuilder();
				builder.append("ba");
				if (pixelsToShow > 0) {
					builder.append("sr");
				}
				builder.append("lr".repeat(Math.max(0, pixelsToShow - 1)));
				text = builder.toString();
			}
			return Component.literal(text).withStyle(
					style -> style.withFont(TEXTURES_FONT).withItalic(false).withShadowColor(0).withColor(ChatFormatting.WHITE)
			);
		}).result().orElseGet(
				() -> occupancy.error().map(e -> Component.literal(e.message()).withStyle(ChatFormatting.RED)).orElseGet(
						() -> Component.literal("missingno")
				)
		);
	}

	public static Fraction getBundleSizeFactor(DataComponentMap components) {
		return components.getOrDefault(BundleComponents.BUNDLE_SIZE_FACTOR, Fraction.ONE);
	}

	public static Fraction getBundleSizeFactor(DataComponentMap.Builder components) {
		return components.getOrDefault(BundleComponents.BUNDLE_SIZE_FACTOR, Fraction.ONE);
	}

	public static Fraction getBundleSizeFactor(ItemStack stack) {
		return getBundleSizeFactor(stack.getComponents());
	}

	private static BundleContents fixBundleInternal(BundleContents bundle, Fraction factor) {
		return BundleHelper.setBundleSizeFactor(
				new BundleContents.Mutable(bundle), factor
		).toImmutable();
	}

	public static BundleContents fixBundle(BundleContents bundle, ItemStack stack) {
		return fixBundle(bundle, getBundleSizeFactor(stack));
	}

	public static BundleContents fixBundle(BundleContents bundle, Fraction factor) {
		if (bundle != null && !BundleHelper.getStoredBundleSizeFactor(bundle).equals(factor)) {
			return fixBundleInternal(bundle, factor);
		}
		return bundle;
	}

	public static void fixBundle(DataComponentMap.Builder components) {
		if (!components.contains(DataComponents.BUNDLE_CONTENTS)) return;
		var factor = getBundleSizeFactor(components);
		var bundle = components.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
		if (!BundleHelper.getStoredBundleSizeFactor(bundle).equals(factor)) {
			components.set(DataComponents.BUNDLE_CONTENTS, fixBundleInternal(bundle, factor));
		}
	}

	public static void fixBundle(ItemStack stack) {
		var factor = getBundleSizeFactor(stack);
		var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null && !BundleHelper.getStoredBundleSizeFactor(bundle).equals(factor)) {
			stack.set(
					DataComponents.BUNDLE_CONTENTS,
					fixBundleInternal(bundle, factor)
			);
		}
	}

	public static BundleContents.Mutable setBundleSizeFactor(
			BundleContents.Mutable builder, Fraction fraction
	) {
		((BundlesComponentExtensions.Internal) builder).metacraft_bundles$setBundleSizeFactor(fraction);
		return builder;
	}

	public static Fraction getStoredBundleSizeFactor(BundleContents bundle) {
		return ((BundlesComponentExtensions) (Object) bundle).metacraft_bundles$getBundleSizeFactor();
	}

	public static Fraction getStoredBundleSizeFactor(BundleContents.Mutable bundle) {
		return ((BundlesComponentExtensions) bundle).metacraft_bundles$getBundleSizeFactor();
	}

	public static void init() {
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((serverStack, clientStack, ctx)-> {
			if (serverStack.has(DataComponents.BUNDLE_CONTENTS) && BundleConfig.getInstance().bundleRendering()) {
				var text = getOccupancyText(serverStack);
				if (text == null) return clientStack;
				var lore = clientStack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
				clientStack.set(DataComponents.LORE, new ItemLore(
						Util.copyAndAdd(text, lore.lines())
				));
			}
			return clientStack;
		});
	}

}

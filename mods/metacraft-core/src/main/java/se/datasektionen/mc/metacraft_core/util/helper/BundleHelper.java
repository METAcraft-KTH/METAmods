package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.math.Fraction;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.item.components.METAcraftComponents;
import se.datasektionen.mc.metacraft_core.extensions.BundlesComponentExtensions;

import java.util.ArrayList;
import java.util.List;

public class BundleHelper {

	private static final int MAX_PIXELS = 94;
	private static final Identifier TEXTURES_FONT = METAcraftCore.getID("bundle_textures");

	public static void updateBundleSizeParameter(ItemStack bundle, Fraction occupancy) {
		var text = getOccupancyText(occupancy);
		var lore = bundle.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT);
		for (int i = 0; i < lore.lines().size(); i++) {
			var line = lore.lines().get(i);
			if (
					line.getStyle().getFont().equals(TEXTURES_FONT)
			) {
				if (text.equals(lore.lines().get(i))) return;
				List<Text> lines = new ArrayList<>(lore.lines());
				lines.set(i, text);
				lore = new LoreComponent(lines);
				bundle.set(DataComponentTypes.LORE, lore);
				return;
			}
		}
		bundle.set(DataComponentTypes.LORE, new LoreComponent(Util.withPrepended(text, lore.lines())));
	}

	public static Text getOccupancyText(Fraction occupancy) {
		int pixelsToShow = MathHelper.multiplyFraction(occupancy, MAX_PIXELS);
		String text;
		if (MAX_PIXELS <= pixelsToShow) {
			text = "ef";
		} else {
			StringBuilder builder = new StringBuilder();
			builder.append("e");
			if (pixelsToShow > 0) {
				builder.append("sr");
			}
			builder.append("lr".repeat(Math.max(0, pixelsToShow - 1)));
			text = builder.toString();
		}
		return Text.literal(text).styled(
				style -> style.withFont(TEXTURES_FONT).withItalic(false).withColor(Formatting.WHITE)
		);
	}

	public static Fraction getBundleSizeFactor(ItemStack stack) {
		return stack.getOrDefault(METAcraftComponents.BUNDLE_SIZE_FACTOR, Fraction.ONE);
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

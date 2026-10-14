package nu.metacraft.bundles.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import nu.metacraft.bundles.BundleComponents;
import nu.metacraft.bundles.util.BundleHelper;
import org.apache.commons.lang3.math.Fraction;

/**
 * The size factor's contract: a bundle whose factor is F holds F times what a vanilla bundle
 * holds, its stored weight is still its fullness in [0, 1] (what the client's bar shows), and
 * the factor survives the immutable ↔ mutable round trip. Run headless:
 * {@code JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-bundles:runServer}.
 */
public final class BundlesGameTests {

	/** A bundle item stack with the given size factor, its contents empty and already carrying the factor. */
	private static ItemStack bundle(int factor) {
		ItemStack stack = new ItemStack(Items.BUNDLE);
		stack.set(BundleComponents.BUNDLE_SIZE_FACTOR, Fraction.getFraction(factor, 1));
		BundleHelper.fixBundle(stack);
		return stack;
	}

	private static BundleContents.Mutable mutable(ItemStack bundle) {
		return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).asMutable();
	}

	private static int count(BundleContents contents) {
		return contents.items().stream().mapToInt(ItemStackTemplate::count).sum();
	}

	@GameTest
	public void factorOneIsVanilla(GameTestHelper helper) {
		BundleContents.Mutable m = mutable(bundle(1));
		if (m.tryInsert(new ItemStack(Items.DIAMOND, 64)) != 64) helper.fail("a vanilla bundle takes 64 diamonds");
		if (!m.weight().equals(Fraction.ONE)) helper.fail("64 diamonds should fill it, weight " + m.weight());
		if (m.tryInsert(new ItemStack(Items.DIAMOND, 1)) != 0) helper.fail("a full bundle took another diamond");
		helper.succeed();
	}

	@GameTest
	public void factorScalesCapacityAndKeepsFullness(GameTestHelper helper) {
		BundleContents.Mutable m = mutable(bundle(2));
		if (BundleHelper.getStoredBundleSizeFactor(m).intValue() != 2) helper.fail("the mutable did not get the factor from asMutable()");
		if (m.tryInsert(new ItemStack(Items.DIAMOND, 64)) != 64) helper.fail("a ×2 bundle takes the first 64 diamonds");
		if (!m.weight().equals(Fraction.getFraction(1, 2))) helper.fail("64 diamonds in a ×2 bundle is half full, weight " + m.weight());
		if (m.tryInsert(new ItemStack(Items.DIAMOND, 64)) != 64) helper.fail("a ×2 bundle takes the second 64 diamonds");
		if (!m.weight().equals(Fraction.ONE)) helper.fail("128 diamonds in a ×2 bundle is full, weight " + m.weight());
		if (m.tryInsert(new ItemStack(Items.DIAMOND, 1)) != 0) helper.fail("a full ×2 bundle took a 129th diamond");
		BundleContents built = m.toImmutable();
		if (count(built) != 128) helper.fail("the immutable holds " + count(built) + " items, not 128");
		if (!built.weight().getOrThrow().equals(Fraction.ONE)) helper.fail("the immutable's weight is " + built.weight() + ", not full");
		if (BundleHelper.getStoredBundleSizeFactor(built).intValue() != 2) helper.fail("the immutable lost the factor");
		BundleContents.Mutable again = built.asMutable();
		if (!again.weight().equals(Fraction.ONE)) helper.fail("round trip changed the weight to " + again.weight());
		if (again.tryInsert(new ItemStack(Items.DIAMOND, 1)) != 0) helper.fail("round trip made room that is not there");
		helper.succeed();
	}

	@GameTest
	public void removingGivesTheRoomBack(GameTestHelper helper) {
		BundleContents.Mutable m = mutable(bundle(2));
		m.tryInsert(new ItemStack(Items.DIAMOND, 64));
		m.tryInsert(new ItemStack(Items.DIAMOND, 64));
		ItemStack out = m.removeOne();
		if (out.isEmpty()) helper.fail("removeOne gave nothing");
		if (!m.weight().equals(Fraction.getReducedFraction(128 - out.getCount(), 128))) helper.fail("after removing " + out.getCount() + " the weight is " + m.weight());
		if (m.tryInsert(new ItemStack(Items.DIAMOND, out.getCount())) != out.getCount()) helper.fail("the removed stack does not fit back");
		helper.succeed();
	}

	@GameTest
	public void stacksNeverExceedTheirMaxSize(GameTestHelper helper) {
		BundleContents.Mutable m = mutable(bundle(4));
		for (int i = 0; i < 3; i++) m.tryInsert(new ItemStack(Items.ENDER_PEARL, 16));
		BundleContents built = m.toImmutable();
		if (count(built) != 48) helper.fail("expected 48 pearls, got " + count(built));
		for (ItemStackTemplate t : built.items()) {
			if (t.count() > 16) helper.fail("a stack of " + t.count() + " ender pearls, max is 16");
		}
		helper.succeed();
	}

	@GameTest
	public void equalityIncludesTheFactor(GameTestHelper helper) {
		BundleContents a = mutable(bundle(1)).toImmutable(), b = mutable(bundle(2)).toImmutable();
		if (a.equals(b)) helper.fail("empty bundles with factors 1 and 2 compare equal");
		if (!a.equals(mutable(bundle(1)).toImmutable())) helper.fail("two empty ×1 bundles compare unequal");
		helper.succeed();
	}

	/** Dyeing a bundle is a vanilla transmute: the result carries the input's components, contents included. */
	@GameTest
	public void transmuteKeepsContents(GameTestHelper helper) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		BundleContents.Mutable m = BundleContents.EMPTY.asMutable();
		m.tryInsert(new ItemStack(Items.DIAMOND, 4));
		bundle.set(DataComponents.BUNDLE_CONTENTS, m.toImmutable());
		ItemStack out = net.minecraft.world.item.crafting.TransmuteRecipe.createWithOriginalComponents(
				ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DYED_BUNDLE.white())), bundle);
		BundleContents kept = out.get(DataComponents.BUNDLE_CONTENTS);
		if (kept == null || kept.isEmpty()) helper.fail("contents lost through a vanilla transmute: " + out + " " + kept);
		if (count(kept) != 4) helper.fail("kept " + count(kept) + " diamonds, not 4");
		helper.succeed();
	}
}

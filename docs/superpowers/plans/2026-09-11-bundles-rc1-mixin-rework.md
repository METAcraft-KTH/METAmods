# Bundles size factor on 26.3-rc-1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `metacraft-bundles`' size-factor mixin works against Minecraft 26.3-rc-1's restructured `BundleContents.Mutable`, proven by game tests, so an ovve (a bundle) can load on branch `26.3`.

**Architecture:** The mixin keeps its contract — a bundle's stored weight is its *fullness* in [0, 1] and every item's weight is divided by the bundle's size factor on the way in — but re-aims every hook at rc-1's code shape: the factor is copied onto a mutable when `asMutable()` creates it (the constructor is private now), the per-item weight scaling wraps the calls the instance methods make (`getWeight`, `getStackedWeight`, `getWeightWithAddedItems`, all now reached from `tryInsert`, `tryTransfer`, `removeOne`, `setItem`, `addSlotWithItem`), and the item list is the superclass's protected field, reached by extending it. No `@Local(name=…)` except the one whose name rc-1 still has (`removedStack`).

**Tech Stack:** Java 25, Fabric Loader 0.19.5, Mixin 0.8.7 + MixinExtras (`@WrapOperation`, `@ModifyReturnValue`, `@Local`), Fabric GameTest API (`net.fabricmc.fabric.api.gametest.v1.GameTest`), Gradle via `./gradlew` from the METAmods root, checkstyle (tabs only).

**Spec:** `docs/superpowers/specs/2026-09-11-post-pass-and-screenshot-tests-design.md`, section "1. Bundles mixin on rc-1".

## Global Constraints

- Branch `26.3` of Froosty11/metamods; the tree is at 26.3-rc-1 (`gradle.properties`: `minecraft_version=26.3-rc-1`, `fabric_json_mc_version=26.3-rc.1`).
- Indentation is tabs; checkstyle fails the build on a line whose indent contains a space. Run `./gradlew :mods:metacraft-bundles:checkstyleMain -q` before every commit.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Game tests run with the server, no Gradle task: `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true -Dfabric-api.gametest.report-file=/tmp/bundles-gametest.xml" ./gradlew :mods:metacraft-bundles:runServer -q` from the METAmods root, then `grep -oE '<failure message="[^"]{0,300}' /tmp/bundles-gametest.xml`. The server prints `All N required tests passed :)` on success. Delete `mods/metacraft-bundles/run/world` between runs.
- rc-1 class shapes this plan relies on (verified with `javap -p -c -l` on `~/.gradle/caches/fabric-loom/26.3-rc-1/minecraft-merged.jar`):
  - `BundleContents.Mutable extends GrowableMutableContainer<BundleContents> extends SimpleMutableContainer<BundleContents>`; `SimpleMutableContainer` has `protected final List<ItemStack> items`; `Mutable` has `private Fraction weight`, `private int selectedItem`, `private boolean needsFlattening`; constructors `private Mutable(List<ItemStack>, Fraction, int)` and `public Mutable()`.
  - `BundleContents.asMutable()` builds the mutable (`Mutable asMutable()` plus a bridge returning `ContainerComponent.Mutable`).
  - `private static Fraction getStackedWeight(ItemStack)` and `private static Fraction getWeightWithAddedItems(Fraction weight, ItemStack itemsToAdd)` in `Mutable`; the latter returns `weight + stacked` when `≤ Fraction.ONE`, else `null`.
  - `tryInsert(ItemStack)` locals: `itemsToAdd`, `maybeItemWeight`, `itemWeight`, `amountToAdd`, `stackIndex`, `removedStack`, `mergedStack`; it calls `BundleContents.getWeight(ItemInstance)` and `ItemStack.copyWithCount(int)`.
  - `findStackIndexWithinRange(ItemStack itemsToAdd, int, int)` calls `ItemStack.isSameItemSameComponents(existing, itemsToAdd)` with the list item first.
  - `BundleContents.getWeight(ItemInstance)` calls the inner bundle's `weight()`; `BundleContents.equals(Object)` calls `List.equals`.
  - `BundleContents` has `private final Supplier<DataResult<Fraction>> weight` and `private static DataResult<Fraction> computeContentWeight(List<? extends ItemInstance>)`.

---

## File structure

- Modify `mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/mixin/BundleContentsMixin.java` — the outer `BundleContents` mixin (factor storage, inner-bundle weight, equality, the new `asMutable` hand-off) and the nested `Mutable` mixin (weight scaling, stack-size cap, immutable build). One file as today; the two classes change together.
- Unchanged: `mixin/BundleContentsAccessor.java` (its `callComputeContentWeight(List<ItemStack>)` and `setWeight(Supplier<DataResult<Fraction>>)` still match rc-1), `util/BundleHelper.java` (already calls `asMutable()`), `metacraft_bundles.mixins.json` (both classes stay listed).
- Create `mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/gametest/BundlesGameTests.java` — the game tests.
- Modify `mods/metacraft-bundles/src/main/resources/fabric.mod.json` — the `fabric-gametest` entrypoint.

---

### Task 1: Game tests that state the contract (and fail today)

**Files:**
- Create: `mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/gametest/BundlesGameTests.java`
- Modify: `mods/metacraft-bundles/src/main/resources/fabric.mod.json` (entrypoints)

**Interfaces:**
- Consumes: `BundleComponents.BUNDLE_SIZE_FACTOR` (`DataComponentType<Fraction>`), `BundleHelper.fixBundle(ItemStack)`, `BundleHelper.getStoredBundleSizeFactor(BundleContents)`, vanilla `BundleContents.Mutable.tryInsert(ItemStack) → int`, `Mutable.weight() → Fraction`, `Mutable.toImmutable() → BundleContents`, `BundleContents.weight() → DataResult<Fraction>`, `BundleContents.items() → List<ItemStackTemplate>`, `ItemStackTemplate.count()`.
- Produces: the four tests below; Task 2 makes them pass.

- [ ] **Step 1: Register the game-test entrypoint**

In `mods/metacraft-bundles/src/main/resources/fabric.mod.json` replace the `entrypoints` block with:

```json
	"entrypoints": {
		"main": [
			"nu.metacraft.bundles.METAcraftBundles"
		],
		"fabric-gametest": [
			"nu.metacraft.bundles.gametest.BundlesGameTests"
		]
	},
```

- [ ] **Step 2: Write the tests**

Create `mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/gametest/BundlesGameTests.java`:

```java
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
}
```

- [ ] **Step 3: Compile and run, expect a mixin failure at startup**

Run from the METAmods root:

```bash
./gradlew :mods:metacraft-bundles:compileJava :mods:metacraft-bundles:checkstyleMain -q
rm -rf mods/metacraft-bundles/run/world
JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true -Dfabric-api.gametest.report-file=/tmp/bundles-gametest.xml" ./gradlew :mods:metacraft-bundles:runServer -q 2>&1 | grep -E 'Mixin apply for mod|required tests|Failed to start' | head -3
```

Expected: compile and checkstyle pass; the server fails to start with `Mixin apply for mod metacraft-bundles failed … BundleContentsMixin$Mutable` (the `<init>` injection and the shadowed `items` have no target in rc-1). No test runs yet. That is the failing state this plan fixes.

- [ ] **Step 4: Commit the tests**

```bash
git add mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/gametest/BundlesGameTests.java mods/metacraft-bundles/src/main/resources/fabric.mod.json
git commit -m "bundles: game tests for the size factor's contract

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Rework the mixin for rc-1

**Files:**
- Modify: `mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/mixin/BundleContentsMixin.java` (whole file)

**Interfaces:**
- Consumes: `BundleContentsAccessor.callComputeContentWeight(List<ItemStack>) → DataResult<Fraction>`, `BundleContentsAccessor.setWeight(Supplier<DataResult<Fraction>>)`, `BundleHelper.runSafeFractionOperation(Supplier<Fraction>) → DataResult<Fraction>`, `BundleHelper.getStoredBundleSizeFactor(BundleContents) → Fraction`, `BundlesComponentExtensions.Internal` (`metacraft_bundles$setBundleSizeFactor(Fraction)`, `metacraft_bundles$getBundleSizeFactor() → Fraction`).
- Produces: `BundleContents` and `BundleContents.Mutable` implement `BundlesComponentExtensions.Internal` at runtime, as before. Nothing else in the mod changes.

- [ ] **Step 1: Replace the file**

Write `mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/mixin/BundleContentsMixin.java`:

```java
package nu.metacraft.bundles.mixin;

import com.google.common.base.Suppliers;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DataResult;
import nu.metacraft.bundles.METAcraftBundles;
import nu.metacraft.bundles.extensions.BundlesComponentExtensions;
import nu.metacraft.bundles.util.BundleHelper;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.GrowableMutableContainer;

/**
 * A bundle's size factor: a bundle whose factor is F holds F times a vanilla bundle's worth.
 * The stored weight stays the bundle's fullness in [0, 1] — that is what the client's bar
 * reads — so every item's weight is divided by F on the way in. 26.3 keeps the weight
 * arithmetic in {@code Mutable}'s private helpers, reached from its instance methods; the
 * hooks wrap those calls where {@code this} (and so the factor) is at hand.
 */
@Mixin(BundleContents.class)
public abstract class BundleContentsMixin implements BundlesComponentExtensions.Internal {

	@Unique
	private Fraction bundleSizeFactor = Fraction.ONE;

	/** An inner bundle weighs its fullness times its own factor: a half-full ×2 bundle is a whole vanilla bundle's worth. */
	@WrapOperation(
			method = "getWeight",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/component/BundleContents;weight()Lcom/mojang/serialization/DataResult;"
			)
	)
	private static DataResult<Fraction> innerBundleWeight(BundleContents inner, Operation<DataResult<Fraction>> original) {
		return original.call(inner).flatMap(
				fraction -> BundleHelper.runSafeFractionOperation(() -> fraction.multiplyBy(BundleHelper.getStoredBundleSizeFactor(inner)))
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

	@ModifyReturnValue(method = "equals", at = @At("RETURN"))
	private boolean equalsIncludingFactor(boolean original, @Local(argsOnly = true) Object other) {
		return original && other instanceof BundleContents contents && this.bundleSizeFactor.equals(BundleHelper.getStoredBundleSizeFactor(contents));
	}

	/** The mutable is made here now (its constructor is private): it starts with this bundle's factor. */
	@ModifyReturnValue(
			method = "asMutable()Lnet/minecraft/world/item/component/BundleContents$Mutable;",
			at = @At("RETURN")
	)
	private BundleContents.Mutable carryFactor(BundleContents.Mutable mutable) {
		((BundlesComponentExtensions.Internal) (Object) mutable).metacraft_bundles$setBundleSizeFactor(this.bundleSizeFactor);
		return mutable;
	}

	@Mixin(BundleContents.Mutable.class)
	public static abstract class Mutable extends GrowableMutableContainer<BundleContents> implements Internal {
		@Shadow private Fraction weight;

		@Unique
		private Fraction bundleSizeFactor = Fraction.ONE;

		protected Mutable(List<ItemStack> items) {
			super(items);
		}

		/** An item weighs a factor-th of its vanilla weight here. */
		@Unique
		private DataResult<Fraction> scaled(DataResult<Fraction> vanilla) {
			return vanilla.flatMap(fraction -> BundleHelper.runSafeFractionOperation(() -> fraction.divideBy(bundleSizeFactor)));
		}

		@WrapOperation(
				method = {
						"tryInsert(Lnet/minecraft/world/item/ItemStack;)I",
						"tryTransfer(Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/entity/player/Player;)I"
				},
				at = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/world/item/component/BundleContents;getWeight(Lnet/minecraft/world/item/ItemInstance;)Lcom/mojang/serialization/DataResult;"
				)
		)
		private DataResult<Fraction> itemWeightOnInsert(net.minecraft.world.item.ItemInstance item, Operation<DataResult<Fraction>> original) {
			return scaled(original.call(item));
		}

		@WrapOperation(
				method = {"removeOne", "setItem"},
				at = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/world/item/component/BundleContents$Mutable;getStackedWeight(Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;"
				)
		)
		private Fraction stackWeightOnRemove(ItemStack stack, Operation<Fraction> original) {
			return original.call(stack).divideBy(bundleSizeFactor);
		}

		/**
		 * {@code getWeightWithAddedItems(w, s)} answers {@code w + s} when that is at most one, else
		 * null; it is static, so the factor cannot reach inside. Feed it {@code F·w − (F − 1)}: it then
		 * accepts exactly when {@code w + s/F ≤ 1}, and its answer maps back as {@code (r + F − 1) / F}.
		 */
		@WrapOperation(
				method = {"setItem", "addSlotWithItem"},
				at = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/world/item/component/BundleContents$Mutable;getWeightWithAddedItems(Lorg/apache/commons/lang3/math/Fraction;Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;"
				)
		)
		private @Nullable Fraction capacityOnSet(Fraction current, ItemStack stack, Operation<Fraction> original) {
			try {
				Fraction slack = bundleSizeFactor.subtract(Fraction.ONE);
				Fraction shifted = current.multiplyBy(bundleSizeFactor).subtract(slack);
				Fraction answer = original.call(shifted, stack);
				return answer == null ? null : answer.add(slack).divideBy(bundleSizeFactor);
			} catch (ArithmeticException overflow) {
				return null;
			}
		}

		@ModifyReturnValue(method = "toImmutable", at = @At("RETURN"))
		private BundleContents build(BundleContents original) {
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

		/** Never merge into a stack that is already at its max size; the list item is the first argument. */
		@WrapOperation(
				method = "findStackIndexWithinRange",
				at = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
				)
		)
		private boolean notIntoAFullStack(ItemStack existing, ItemStack adding, Operation<Boolean> original) {
			return existing.getCount() < existing.getMaxStackSize() && original.call(existing, adding);
		}

		/**
		 * {@code toImmutable()} merges identical stacks it finds; two half stacks must not become one
		 * past the max size. (Reached only after slot edits set {@code needsFlattening}; the game tests
		 * cover the insert path, this guard the GUI path.)
		 */
		@WrapOperation(
				method = "mergeIdenticalStacks",
				at = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/world/item/component/BundleContents$Mutable;findStackIndexWithinRange(Lnet/minecraft/world/item/ItemStack;II)I"
				)
		)
		private int noMergePastMaxSize(BundleContents.Mutable self, ItemStack stack, int from, int to, Operation<Integer> original) {
			int index = original.call(self, stack, from, to);
			return index >= 0 && items.get(index).getCount() + stack.getCount() > stack.getMaxStackSize() ? -1 : index;
		}

		/** A merge that would pass the max stack size is split: the full stack goes back in front, the rest is the merge. */
		@ModifyArg(
				method = "tryInsert(Lnet/minecraft/world/item/ItemStack;)I",
				at = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"
				)
		)
		private int splitAtMaxSize(int total, @Local(name = "removedStack") ItemStack removedStack) {
			if (total > removedStack.getMaxStackSize()) {
				items.addFirst(removedStack.copyWithCount(removedStack.getMaxStackSize()));
				return total - removedStack.getMaxStackSize();
			}
			return total;
		}
	}
}
```

- [ ] **Step 2: Compile and checkstyle**

```bash
./gradlew :mods:metacraft-bundles:compileJava :mods:metacraft-bundles:checkstyleMain -q
```

Expected: no output. If javac complains that `ItemInstance` is not found, the import is `net.minecraft.world.item.ItemInstance` (the fully qualified name in the parameter above avoids a second import line; either form is fine).

- [ ] **Step 3: Run the game tests**

```bash
rm -rf mods/metacraft-bundles/run/world
JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true -Dfabric-api.gametest.report-file=/tmp/bundles-gametest.xml" ./gradlew :mods:metacraft-bundles:runServer -q 2>&1 | grep -E 'Mixin apply for mod|required tests|Failed to start|Caused by' | head -5
grep -oE '<failure message="[^"]{0,300}' /tmp/bundles-gametest.xml
```

Expected: `All 5 required tests passed :)` and no `<failure` lines. If a mixin fails to apply, the log names the injector; the targets above were read from the rc-1 bytecode, so a failure means the method list or descriptor was mistyped — compare against `javap -p -c -l 'net.minecraft.world.item.component.BundleContents$Mutable'` from the jar in Global Constraints.

- [ ] **Step 4: Run moredyes' bundle test too**

moredyes crafts a coloured bundle (`bundleDye`) through the same code:

```bash
rm -rf mods/moredyes/run/world
JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true -Dfabric-api.gametest.report-file=/tmp/moredyes-gametest.xml" ./gradlew :mods:moredyes:runServer -q 2>&1 | grep -E 'required tests|Failed to start' | head -2
grep -oE '<failure message="[^"]{0,300}' /tmp/moredyes-gametest.xml
```

Expected: all moredyes tests pass. A failure in a non-bundle moredyes test is a separate rc-1 issue; note it and continue.

- [ ] **Step 5: Commit**

```bash
git add mods/metacraft-bundles/src/main/java/nu/metacraft/bundles/mixin/BundleContentsMixin.java
git commit -m "bundles: size factor on 26.3-rc-1 — hooks re-aimed at the restructured mutable

The factor rides onto the mutable from asMutable() (the constructor is private now); item
weights are scaled where the instance methods reach the static helpers; the item list is the
superclass's. Fullness stays the stored weight, as the client's bar expects.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Ovvar runs on rc-1

**Files:**
- None to edit. Verification only; ovvar's game tests already cover its behaviour.

**Interfaces:**
- Consumes: Task 2's working bundle. Ovvar's `OvveItem extends BundleItem`.

- [ ] **Step 1: Run ovvar's game tests on rc-1**

```bash
./gradlew :mods:ovvar:runDatagen :mods:ovvar:checkstyleMain -q
rm -rf mods/ovvar/run/world
JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true -Dfabric-api.gametest.report-file=/tmp/ovvar-gametest.xml" ./gradlew :mods:ovvar:runServer -q 2>&1 | grep -E 'required tests|Mixin apply for mod|Failed to start|Caused by' | head -5
grep -oE '<failure message="[^"]{0,300}' /tmp/ovvar-gametest.xml
```

Expected: `All 9 required tests passed :)`. Before Task 2 this run died with `ClassCastException: BundleContents cannot be cast to BundlesComponentExtensions` or the mixin apply error; both are gone now. Any other failure is new rc-1 breakage in ovvar: read the stack, fix in ovvar, add to this task's commit.

- [ ] **Step 2: Update the port status note**

In `docs/superpowers/specs/2026-09-11-post-pass-and-screenshot-tests-design.md` no change. In the memory file `26-3-port-status.md` (outside the repo, at `~/.claude-work2/projects/-Users-winsty-dev-Personal-Projects-MinecraftModding-Metacraft-METAmods/memory/`), replace the sentence about blocker (1) with: "(1) done 2026-09-11: BundleContentsMixin reworked, 5 bundle game tests, ovvar's 9 game tests pass on rc-1."

- [ ] **Step 3: Commit anything Step 1 changed**

Only if Step 1 required an ovvar fix:

```bash
git add mods/ovvar
git commit -m "ovvar: <what broke> on 26.3-rc-1

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

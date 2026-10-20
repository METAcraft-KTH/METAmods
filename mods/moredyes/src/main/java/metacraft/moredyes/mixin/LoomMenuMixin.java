package metacraft.moredyes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import metacraft.moredyes.banner.BannerPatterns;
import metacraft.moredyes.content.item.ModDyeItem;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Our dyes in vanilla's loom. The loom takes a dye by its {@code dye} component, which our dyes do not
 * have on the server (nothing else on the server should take them for a vanilla colour), so its dye
 * slot and shift-click are told here; and where vanilla would add the pattern in the dye's colour, a dye
 * of ours adds the pattern's derived version in our colour, with WHITE (see {@link BannerPatterns}).
 * Pattern items and using up one banner and one dye per take stay vanilla's. The six-layer cap does too,
 * except that a banner in one of our colours does not count its colour: it takes six patterns on top
 * (seven layers, {@link BannerPatterns#layerCap}), with any dye.
 *
 * <p>The vanilla client runs the same menu on its own view of the items: there our dye is a white dye
 * with the nearest vanilla colour's {@code dye} component ({@link ModDyeItem#modifyBasePolymerItemStack}),
 * so it goes in the dye slot and the patterns are listed; the server's result replaces the client's guess.
 * The client's loom screen caps at six layers on its own, so a coloured banner with five patterns on top is
 * shown to it without its colour layer ({@link BannerPatterns#loomClientView}, {@link LoomBannerViewMixin}).
 */
@Mixin(LoomMenu.class)
public abstract class LoomMenuMixin {
	@Shadow @Final private Slot bannerSlot;
	@Shadow @Final private Slot dyeSlot;
	@Shadow @Final private Slot resultSlot;
	@Shadow @Final private HolderGetter<BannerPattern> patternGetter;

	/** The dye slot's {@code mayPlace} and shift-click both ask this. */
	@Inject(method = "isDyeItem", at = @At("HEAD"), cancellable = true)
	private static void moredyes$ourDyes(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() instanceof ModDyeItem) cir.setReturnValue(true);
	}

	/** The six-layer cap in {@code slotsChanged}: one more for a banner whose first layer is our colour. */
	@ModifyExpressionValue(method = "slotsChanged", at = @At(value = "CONSTANT", args = "intValue=6"))
	private int moredyes$colourIsNotAPattern(int max) {
		return BannerPatterns.layerCap(bannerSlot.getItem().getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
	}

	@Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
	private void moredyes$inOurColour(Holder<BannerPattern> pattern, CallbackInfo ci) {
		if (!(dyeSlot.getItem().getItem() instanceof ModDyeItem dye)) return;
		ci.cancel();
		ItemStack banner = bannerSlot.getItem();
		Holder<BannerPattern> derived = BannerPatterns.derivedOf(patternGetter, dye.color(), pattern);
		ItemStack result = ItemStack.EMPTY;
		if (!banner.isEmpty() && derived != null) {
			result = banner.copyWithCount(1);
			result.update(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY,
					layers -> new BannerPatternLayers.Builder().addAll(layers).add(derived, DyeColor.WHITE).build());
		}
		if (!ItemStack.matches(result, resultSlot.getItem())) resultSlot.set(result);
	}
}

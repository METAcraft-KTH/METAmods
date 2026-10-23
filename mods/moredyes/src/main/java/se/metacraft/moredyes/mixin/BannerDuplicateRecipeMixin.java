package se.metacraft.moredyes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import se.metacraft.moredyes.banner.BannerPatterns;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Copying a banner refuses one with more than six layers. A banner in one of our colours carries the
 * colour as a layer and can hold six patterns on top of it (a loom puts them there, see
 * {@link LoomMenuMixin}), so for it the limit is six patterns, not six layers.
 */
@Mixin(BannerDuplicateRecipe.class)
public abstract class BannerDuplicateRecipeMixin {
	@ModifyExpressionValue(method = {"matches", "assemble"}, at = @At(value = "CONSTANT", args = "intValue=6"))
	private int moredyes$colourIsNotAPattern(int max, @Local ItemStack stack) {
		return BannerPatterns.layerCap(stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
	}
}

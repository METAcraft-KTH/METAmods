package se.metacraft.moredyes.mixin;

import se.metacraft.moredyes.banner.BannerPatterns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Washing a banner in a cauldron takes its top layer off, but never its colour. A banner in one of
 * our colours carries that colour as its first layer, so washing stops there too: a plain Cerise
 * banner washes like a plain red one (nothing happens), and a patterned one washes back to plain Cerise.
 */
@Mixin(CauldronInteractions.class)
public abstract class CauldronInteractionsMixin {
	@Inject(method = "bannerInteraction", at = @At("HEAD"), cancellable = true)
	private static void moredyes$keepOurColour(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			ItemStack stack, CallbackInfoReturnable<InteractionResult> cir) {
		var layers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
		if (layers.size() == 1 && BannerPatterns.isColouredBase(layers.getFirst())) {
			cir.setReturnValue(InteractionResult.TRY_WITH_EMPTY_HAND);
		}
	}
}

package se.metacraft.moredyes.compat.polydecorations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.mapcanvas.api.core.CanvasColor;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import eu.pb4.mapcanvas.api.utils.VirtualDisplay;
import eu.pb4.polydecorations.entity.CanvasEntity;
import eu.pb4.polydecorations.item.DecorationsItemTags;
import se.metacraft.moredyes.brush.BrushColour;
import se.metacraft.moredyes.content.item.ModDyeItem;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Paint a PolyDecorations canvas with our dyes. The canvas takes anything in {@code #c:dyes} with
 * vanilla's {@code dye} component; ours have neither (a vanilla colour would be wrong, and other mods
 * read {@code #c:dyes} too), so the canvas is told here: a dye of ours paints its colour's nearest
 * map colour, at normal brightness like a vanilla dye, to lighten or darken from there. A canvas has
 * only the fixed map palette and cannot animate, so an animated dye paints a random one of its
 * keyframes' map colours each time, which reads as confetti in its colours.
 * A brush can copy one of those shades and paint it back ({@link BrushColour}); copying works on a waxed canvas too.
 */
@Mixin(CanvasEntity.class)
public abstract class CanvasEntityMixin {
	@Shadow private DrawableCanvas selfCanvas;
	@Shadow private boolean cut;

	@Shadow protected abstract CanvasColor getColor(byte raw);

	@Inject(method = "getColor(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private static void moredyes$ourDyesPaint(ItemStack stack, CallbackInfoReturnable<Optional<CanvasColor>> cir) {
		if (BrushColour.isColoured(stack)) {
			cir.setReturnValue(Optional.ofNullable(CanvasUtils.findClosestColor(BrushColour.rgb(stack))));
		} else if (stack.getItem() instanceof ModDyeItem dye) {
			MapColor map = dye.color().mapColor();
			var animation = dye.color().animation();
			if (animation.isPresent()) {
				List<MapColor> keyframes = animation.get().mapColors();
				map = keyframes.get(ThreadLocalRandom.current().nextInt(keyframes.size()));
			}
			cir.setReturnValue(Optional.ofNullable(CanvasColor.from(map, MapColor.Brightness.NORMAL)));
		}
	}

	@WrapOperation(method = "onUsed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean moredyes$ourDyesAreDyes(ItemStack stack, TagKey<Item> tag, Operation<Boolean> original) {
		return original.call(stack, tag) || (tag == ConventionalItemTags.DYES
				&& (stack.getItem() instanceof ModDyeItem || BrushColour.isColoured(stack)));
	}

	/** A coloured brush paints its own colour, one pixel, instead of being the offhand's 3x3 tool. */
	@WrapOperation(method = "onUsed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
	private boolean moredyes$colouredBrushPaints(ItemStack stack, Object item, Operation<Boolean> original,
			@Local(argsOnly = true) ServerPlayer player) {
		return original.call(stack, item) && !(BrushColour.isColoured(stack) && BrushColour.inUse(player, moredyes$offhandIsCanvasTool(player)));
	}

	private static boolean moredyes$offhandIsCanvasTool(ServerPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		return !offhand.isEmpty() && (CanvasEntity.getColor(offhand).isPresent()
				|| offhand.is(DecorationsItemTags.CANVAS_CLEAR_PIXELS) || offhand.is(DecorationsItemTags.CANVAS_DARKEN_PIXELS)
				|| offhand.is(DecorationsItemTags.CANVAS_LIGHTEN_PIXELS));
	}

	/** The canvas colour nearest the average of the pixels within {@code radius}, skipping cut-out and off-canvas ones. */
	private int moredyes$average(int px, int py, int radius) {
		int r = 0, g = 0, b = 0, n = 0;
		for (int y = py - radius; y <= py + radius; y++) {
			for (int x = px - radius; x <= px + radius; x++) {
				if (x < 0 || x >= selfCanvas.getWidth() || y < 0 || y >= selfCanvas.getHeight()) continue;
				if (cut && selfCanvas.get(x, y) == CanvasColor.CLEAR_FORCE) continue;
				int c = getColor(selfCanvas.getRaw(x, y)).getRgbColor();
				r += c >> 16 & 0xFF;
				g += c >> 8 & 0xFF;
				b += c & 0xFF;
				n++;
			}
		}
		int average = (r / n) << 16 | (g / n) << 8 | b / n;
		CanvasColor closest = CanvasUtils.findClosestColor(average);
		return closest != null ? closest.getRgbColor() : average;
	}

	/** The hand whose brush takes a sampled colour, or null when this click is not a sample. */
	private static InteractionHand moredyes$samplingHand(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack offhand = player.getOffhandItem();
		boolean sneaking = player.isShiftKeyDown();
		if (offhand.is(Items.BRUSH) && (sneaking || !BrushColour.isColoured(offhand) && !BrushColour.isColoured(main))) {
			return InteractionHand.OFF_HAND;
		}
		boolean paints = BrushColour.isColoured(main) && !sneaking;
		return BrushColour.inUse(player, moredyes$offhandIsCanvasTool(player)) && !paints ? InteractionHand.MAIN_HAND : null;
	}

	@Inject(method = "onUsed", at = @At("HEAD"), cancellable = true)
	private void moredyes$sampleColour(ServerPlayer player, VirtualDisplay.ClickType click, int x, int y, CallbackInfo ci) {
		if (click.isLeft() || !player.getMainHandItem().is(Items.BRUSH)) return;
		InteractionHand hand = moredyes$samplingHand(player);
		if (hand == null) return;
		ci.cancel();
		// 8 click units to a pixel
		int px = x / 8, py = y / 8;
		if (px < 0 || px >= selfCanvas.getWidth() || py < 0 || py >= selfCanvas.getHeight()) return;
		// a cut-out pixel has no colour, so sampling it blanks the brush
		if (cut && selfCanvas.get(px, py) == CanvasColor.CLEAR_FORCE) {
			BrushColour.strip(player, hand);
			return;
		}
		// the offhand brush takes the 3x3 average
		BrushColour.sample(player, hand, moredyes$average(px, py, hand == InteractionHand.OFF_HAND ? 1 : 0));
	}
}

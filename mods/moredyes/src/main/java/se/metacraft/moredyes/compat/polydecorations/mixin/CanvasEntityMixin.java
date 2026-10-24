package se.metacraft.moredyes.compat.polydecorations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import eu.pb4.mapcanvas.api.core.CanvasColor;
import eu.pb4.polydecorations.entity.CanvasEntity;
import se.metacraft.moredyes.content.item.ModDyeItem;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
 */
@Mixin(CanvasEntity.class)
public abstract class CanvasEntityMixin {
	@Inject(method = "getColor(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
	private static void moredyes$ourDyesPaint(ItemStack stack, CallbackInfoReturnable<Optional<CanvasColor>> cir) {
		if (stack.getItem() instanceof ModDyeItem dye) {
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
		return original.call(stack, tag) || (tag == ConventionalItemTags.DYES && stack.getItem() instanceof ModDyeItem);
	}
}

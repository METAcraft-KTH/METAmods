package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.block.entity.BannerPattern;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import se.datasektionen.mc.simplecustomfeatures.objects.items.BannerPatternItemObject;

import java.util.List;

@Mixin(LoomScreenHandler.class)
public class MixinLoomScreenHandler {

	@Shadow @Final private Slot patternSlot;

	@Shadow private List<RegistryEntry<BannerPattern>> bannerPatterns;
	@Unique
	int prevIndex = -1;

	@ModifyArg(
		method = "onContentChanged",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/screen/Property;set(I)V"
		),
		slice = @Slice(
			from = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/screen/LoomScreenHandler;getPatternsFor(Lnet/minecraft/item/ItemStack;)Ljava/util/List;"
			),
			to = @At(
				value = "FIELD",
				target = "Lnet/minecraft/component/DataComponentTypes;BANNER_PATTERNS:Lnet/minecraft/component/ComponentType;"
			)
		)
	)
	public int onContentChanged(int original) {
		if (this.patternSlot.getStack().getItem() instanceof BannerPatternItemObject.CustomBannerPatternItem) {
			if (original == -1) {
				prevIndex = (prevIndex + 1) % this.bannerPatterns.size();
				return prevIndex;
			} else {
				prevIndex = original;
			}
		}
		return original;
	}

}

package metacraft.moredyes.mixin;

import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.content.ModContent;
import metacraft.moredyes.cushion.Cushions;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A cushion placed from our item takes our colour; one in our colour drops (and picks) our item. */
@Mixin(Cushion.class)
public abstract class CushionMixin {
	@Inject(method = "applyImplicitComponents", at = @At("TAIL"))
	private void moredyes$takeOurColour(DataComponentGetter components, CallbackInfo ci) {
		String color = components.get(Cushions.ITEM_COLOR);
		if (color != null) ((Cushion) (Object) this).setAttached(Cushions.COLOR, color);
	}

	@Inject(method = {"getCushionItemStackWithData", "getPickResult"}, at = @At("HEAD"), cancellable = true)
	private void moredyes$ourItem(CallbackInfoReturnable<ItemStack> cir) {
		Cushion self = (Cushion) (Object) this;
		ModColor color = Cushions.colour(self);
		if (color == null) return;
		ItemStack stack = new ItemStack(ModContent.cushion(color));
		stack.set(DataComponents.CUSTOM_NAME, self.getCustomName());
		cir.setReturnValue(stack);
	}
}

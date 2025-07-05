package nu.metacraft.plots.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.item.PlotKey;

@Mixin(ForgingScreenHandler.class)
public class MixinForgingScreenHandler {

	@ModifyArg(
		method = "quickMove",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/screen/ForgingScreenHandler;insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z",
			ordinal = 0
		),
		index = 0
	)
	public ItemStack quickMove(ItemStack stack, @Local(argsOnly = true) PlayerEntity player) {
		if ((Object) this instanceof AnvilScreenHandler) {
			if (stack.isOf(PlotItems.PLOT_KEY) && stack.contains(DataComponentTypes.CUSTOM_NAME)) {
				PlotKey.renameKey(stack, player.getServer(), stack.getName().getString());
			}
		}
		return stack;
	}

}

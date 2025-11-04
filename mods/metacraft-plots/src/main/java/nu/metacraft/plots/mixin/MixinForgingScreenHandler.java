package nu.metacraft.plots.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import nu.metacraft.plots.item.PlotItems;
import nu.metacraft.plots.item.PlotKey;

@Mixin(ItemCombinerMenu.class)
public class MixinForgingScreenHandler {

	@ModifyArg(
		method = "quickMoveStack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/ItemCombinerMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z",
			ordinal = 0
		),
		index = 0
	)
	public ItemStack quickMove(ItemStack stack, @Local(argsOnly = true) Player player) {
		if ((Object) this instanceof AnvilMenu) {
			if (stack.is(PlotItems.PLOT_KEY) && stack.has(DataComponents.CUSTOM_NAME)) {
				PlotKey.renameKey(stack, player.level().getServer(), stack.getHoverName().getString());
			}
		}
		return stack;
	}

}

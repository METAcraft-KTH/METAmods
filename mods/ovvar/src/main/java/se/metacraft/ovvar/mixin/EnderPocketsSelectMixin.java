package se.metacraft.ovvar.mixin;

import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.ovvar.content.enchantment.EnderPockets;

/**
 * Scrolling through an Ender Pockets ovve's preview selects an entry of the viewer's ender chest, not of
 * the ovve's own (empty) contents: the selection the client sends ({@code handleBundleItemSelectedPacket})
 * is kept on the ovve as {@code ovvar:ender_selected}, toggled the way the bundle toggles its own.
 */
@Mixin(BundleItem.class)
public abstract class EnderPocketsSelectMixin {
	@Inject(method = "toggleSelectedItem", at = @At("HEAD"), cancellable = true)
	private static void ovvar$selectInTheEnderChest(ItemStack stack, int index, CallbackInfo info) {
		if (EnderPockets.has(stack)) {
			EnderPockets.toggleSelected(stack, index);
			info.cancel();
		}
	}
}

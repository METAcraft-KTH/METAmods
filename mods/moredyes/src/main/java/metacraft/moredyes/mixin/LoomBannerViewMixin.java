package metacraft.moredyes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import metacraft.moredyes.banner.BannerPatterns;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * A loom's banner slot as the client sees it: {@link BannerPatterns#loomClientView}, so that the vanilla
 * client's loom screen lists patterns for the sixth on a banner in our colour. Only that slot of an open
 * loom, and only what is sent: the menu's own slot keeps the real banner.
 *
 * <p>The server remembers what it last sent each slot so it sends only changes; here it remembers the
 * view. When the player puts the real banner in (the client says it now holds that), it no longer matches,
 * and the view goes out. When the player takes the banner out, the cursor holds the view for a round trip,
 * until the server sends the real banner it has on the cursor.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class LoomBannerViewMixin {
	@Shadow
	private void synchronizeSlotToRemote(int slot, ItemStack stack, Supplier<ItemStack> copy) {
		throw new AssertionError();
	}

	/** Every change of a slot, after the first sync. */
	@Inject(method = "synchronizeSlotToRemote", at = @At("HEAD"), cancellable = true)
	private void moredyes$loomBannerView(int slot, ItemStack stack, Supplier<ItemStack> copy, CallbackInfo ci) {
		if (!((Object) this instanceof LoomMenu loom) || slot != loom.getBannerSlot().index) return;
		ItemStack view = BannerPatterns.loomClientView(stack);
		if (view == stack) return;
		ci.cancel();
		synchronizeSlotToRemote(slot, view, view::copy);
	}

	/** The first sync, on opening, and a full resync. */
	@WrapOperation(method = "sendAllDataToRemote",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack moredyes$loomBannerViewInitial(Slot slot, Operation<ItemStack> original) {
		ItemStack stack = original.call(slot);
		return (Object) this instanceof LoomMenu loom && slot == loom.getBannerSlot() ? BannerPatterns.loomClientView(stack) : stack;
	}
}

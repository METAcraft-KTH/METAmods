package metacraft.ovvar.mixin;

import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.OvveTopItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The companion top yields to a real chestplate by swapping onto the cursor. Clear it in the same
 * click that put it there, before the click's result is sent, so it is never held for a tick. The
 * click runs server-side for a ServerPlayer, so the empty cursor reaches the client in one round
 * trip rather than at the next end-of-tick sweep ({@code OvveTop} keeps that as a backstop).
 */
@Mixin(AbstractContainerMenu.class)
public abstract class CompanionCursorMixin {
	@Shadow public abstract ItemStack getCarried();

	@Shadow public abstract void setCarried(ItemStack stack);

	@Inject(method = "clicked", at = @At("TAIL"))
	private void ovvar$dropCompanionFromCursor(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		// Likewise a stash session's fake patch or shears: they live in their hotbar slots and nowhere else.
		if (getCarried().getItem() instanceof OvveTopItem || getCarried().has(ModComponents.SESSION)) {
			setCarried(ItemStack.EMPTY);
		}
	}
}

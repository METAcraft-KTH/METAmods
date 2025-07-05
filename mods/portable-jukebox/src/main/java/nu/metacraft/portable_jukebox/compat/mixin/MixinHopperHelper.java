package nu.metacraft.portable_jukebox.compat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.hopper.HopperHelper;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(HopperHelper.class)
public class MixinHopperHelper {

	@Inject(
		method = "tryMoveSingleItem(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/SidedInventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/inventory/Inventory;setStack(ILnet/minecraft/item/ItemStack;)V"
		)
	)
	private static void tryMoveSingleItem(
			Inventory to, SidedInventory toSided,
			ItemStack transferStack, ItemStack transferChecker,
			int targetSlot, Direction fromDirection, CallbackInfoReturnable<Boolean> cir,
			@Local(ordinal = 3) ItemStack singleItem
	) {
		PortableJukeboxEntity.transferToInventoryFromUnknown(singleItem, to);
	}

}

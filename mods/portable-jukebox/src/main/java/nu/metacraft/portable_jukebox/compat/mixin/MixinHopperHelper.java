package nu.metacraft.portable_jukebox.compat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.hopper.HopperHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(HopperHelper.class)
public class MixinHopperHelper {

	@Inject(
		method = "tryMoveSingleItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/WorldlyContainer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V"
		)
	)
	private static void tryMoveSingleItem(
			Container to, WorldlyContainer toSided,
			ItemStack transferStack, ItemStack transferChecker,
			int targetSlot, Direction fromDirection, CallbackInfoReturnable<Boolean> cir,
			@Local(ordinal = 3) ItemStack singleItem
	) {
		PortableJukeboxEntity.transferToInventoryFromUnknown(singleItem, to);
	}

}

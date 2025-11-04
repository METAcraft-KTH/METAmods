package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(HopperBlockEntity.class)
public class MixinHopperBlockEntity {

	@Inject(
		method = "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V"
		)
	)
	private static void transfer(Container from, Container to, ItemStack stack, int slot, Direction side, CallbackInfoReturnable<ItemStack> cir) {
		PortableJukeboxEntity.transferToInventoryFromUnknown(stack, to);
	}

}

package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.BundleContents;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(BundleContents.Mutable.class)
public class BundleContentsMutableMixin {

	@Inject(
		method = "tryTransfer(Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/entity/player/Player;)I",
		at = @At("RETURN")
	)
	public void onAdd(
			Slot slot, Player player, CallbackInfoReturnable<Integer> cir
	) {
		var bundleStacks = ((SimpleMutableContainerAccessor) this).getItems();
		if (!bundleStacks.isEmpty()) {
			PortableJukeboxEntity.transferToInventory(
					EntityRef.fromEntity(player), bundleStacks.getFirst(), slot.container
			);
		}
	}

}

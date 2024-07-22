package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(BundleContentsComponent.Builder.class)
public class MixinBundleContentsComponentBuilder {

	@Inject(
		method = "add(Lnet/minecraft/screen/slot/Slot;Lnet/minecraft/entity/player/PlayerEntity;)I",
		at = @At("RETURN")
	)
	public void onAdd(
			Slot slot, PlayerEntity player, CallbackInfoReturnable<Integer> cir
	) {
		var bundleStacks = ((AccessorBundleContentsComponentBuilder) this).getStacks();
		if (!bundleStacks.isEmpty()) {
			PortableJukeboxEntity.transferToInventory(
					EntityRef.fromEntity(player), bundleStacks.getFirst(), slot.inventory
			);
		}
	}

}

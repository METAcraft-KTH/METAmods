package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ItemFrameEntity.class)
public abstract class MixinItemFrameEntity {

	@Shadow public abstract ItemStack getHeldItemStack();

	@Inject(
		method = "interact",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/item/ItemStack;decrementUnlessCreative(ILnet/minecraft/entity/LivingEntity;)V"
		)
	)
	public void interact(
			PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir
	) {
		PortableJukeboxEntity.transfer(
				EntityRef.fromEntity(player),
				EntityRef.fromEntity((ItemFrameEntity) (Object) this),
				this.getHeldItemStack()
		);
	}

}

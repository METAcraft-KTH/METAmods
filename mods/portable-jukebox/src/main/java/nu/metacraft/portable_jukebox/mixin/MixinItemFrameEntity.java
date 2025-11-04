package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ItemFrame.class)
public abstract class MixinItemFrameEntity {

	@Shadow public abstract ItemStack getItem();

	@Inject(
		method = "interact",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"
		)
	)
	public void interact(
			Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir
	) {
		PortableJukeboxEntity.transfer(
				EntityRef.fromEntity(player),
				EntityRef.fromEntity((ItemFrame) (Object) this),
				this.getItem()
		);
	}

}

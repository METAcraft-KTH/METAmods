package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_lib.util.EntityRef;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ArmorStandEntity.class)
public abstract class MixinArmorStandEntity {

	@Shadow public abstract ItemStack getEquippedStack(EquipmentSlot slot);

	@Inject(
		method = "equip", at = @At("RETURN")
	)
	public void equip(
			PlayerEntity player, EquipmentSlot slot, ItemStack stack, Hand hand, CallbackInfoReturnable<Boolean> cir
	) {
		if (this.getEquippedStack(slot) == stack) {
			PortableJukeboxEntity.transfer2Way(
					EntityRef.fromEntity(player), stack,
					EntityRef.fromEntity((ArmorStandEntity) (Object) this), player.getStackInHand(hand)
			);
		}
	}

}

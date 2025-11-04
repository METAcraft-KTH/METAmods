package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import nu.metacraft.lib.util.EntityRef;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(ArmorStand.class)
public abstract class MixinArmorStandEntity extends LivingEntity {

	protected MixinArmorStandEntity(EntityType<? extends LivingEntity> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(
		method = "swapItem", at = @At("RETURN")
	)
	public void equip(
			Player player, EquipmentSlot slot, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<Boolean> cir
	) {
		if (this.getItemBySlot(slot) == stack) {
			PortableJukeboxEntity.transfer2Way(
					EntityRef.fromEntity(player), stack,
					EntityRef.fromEntity((ArmorStand) (Object) this), player.getItemInHand(hand)
			);
		}
	}

}

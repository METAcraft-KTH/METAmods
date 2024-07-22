package se.datasektionen.mc.portable_jukebox.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(MobEntity.class)
public abstract class MixinMobEntity extends LivingEntity {
	protected MixinMobEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}
	@Inject(
			method = "equipStack",
			at = @At("HEAD")
	)
	protected void equipStack(
			EquipmentSlot slot, ItemStack stack, CallbackInfo ci
	) {
		PortableJukeboxEntity.transferToEntityFromUnknown(stack, this);
	}


}

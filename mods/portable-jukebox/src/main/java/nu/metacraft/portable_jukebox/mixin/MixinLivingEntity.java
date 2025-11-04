package nu.metacraft.portable_jukebox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {


	public MixinLivingEntity(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Inject(
			method = "setItemSlot",
			at = @At("HEAD")
	)
	protected void equipStack(
			EquipmentSlot slot, ItemStack stack, CallbackInfo ci
	) {
		PortableJukeboxEntity.transferToEntityFromUnknown(stack, this);
	}


}

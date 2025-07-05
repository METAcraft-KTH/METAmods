package nu.metacraft.portable_jukebox.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.portable_jukebox.entity.PortableJukeboxEntity;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {


	public MixinLivingEntity(EntityType<?> type, World world) {
		super(type, world);
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

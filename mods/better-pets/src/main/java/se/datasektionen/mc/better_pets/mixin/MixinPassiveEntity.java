package se.datasektionen.mc.better_pets.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.better_pets.AttributeModifiers;

@Mixin(PassiveEntity.class)
public abstract class MixinPassiveEntity extends PathAwareEntity {

	@Shadow protected int breedingAge;

	protected MixinPassiveEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "onGrowUp", at = @At("HEAD"))
	public void onGrowUp(CallbackInfo ci) {
		if ((Object) this instanceof ParrotEntity) {
			var scale = this.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
			if (breedingAge < 0) {
				if (!scale.hasModifier(AttributeModifiers.BABY_PARROT.id())) {
					scale.addPersistentModifier(AttributeModifiers.BABY_PARROT);
				}
			} else {
				scale.removeModifier(AttributeModifiers.BABY_PARROT.id());
			}
		}
	}

}

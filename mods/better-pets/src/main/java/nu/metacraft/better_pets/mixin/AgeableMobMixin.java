package nu.metacraft.better_pets.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.level.Level;
import nu.metacraft.better_pets.AttributeModifiers;

@Mixin(AgeableMob.class)
public abstract class AgeableMobMixin extends PathfinderMob {

	@Shadow protected int age;

	protected AgeableMobMixin(EntityType<? extends PathfinderMob> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "ageBoundaryReached", at = @At("HEAD"))
	public void onGrowUp(CallbackInfo ci) {
		if ((Object) this instanceof Parrot) {
			var scale = this.getAttribute(Attributes.SCALE);
			if (age < 0) {
				if (!scale.hasModifier(AttributeModifiers.BABY_PARROT.id())) {
					scale.addPermanentModifier(AttributeModifiers.BABY_PARROT);
				}
			} else {
				scale.removeModifier(AttributeModifiers.BABY_PARROT.id());
			}
		}
	}

}

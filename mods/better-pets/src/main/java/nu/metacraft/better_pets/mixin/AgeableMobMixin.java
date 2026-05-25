package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.parrot.Parrot;
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

	@ModifyExpressionValue(
			method = "makeAgeLockedParticle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;isClientSide()Z"
			)
	)
	private static boolean makeAgeLockedParticleOnServerForParrots(
			boolean original, @Local(argsOnly = true, name = "mob") Mob mob
	) {
		if (mob instanceof Parrot) {
			return true;
		}
		return original;
	}

	@WrapWithCondition(
			method = "makeAgeLockedParticle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
			)
	)
	private static boolean sendAgeLockedParticleFromServerForParrots(
			Level instance, ParticleOptions particle,
			double x, double y, double z, double xd, double yd, double zd,
			@Local(argsOnly = true, name = "mob") Mob mob
	) {
		if (mob instanceof Parrot && instance instanceof ServerLevel sl) {
			sl.sendParticles(particle, x, y, z, 1, xd, yd, zd, 0);
			return false;
		}
		return true;
	}

}

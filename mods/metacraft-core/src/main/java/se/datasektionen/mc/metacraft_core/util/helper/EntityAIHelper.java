package se.datasektionen.mc.metacraft_core.util.helper;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.sound.SoundEvent;

public class EntityAIHelper {

	/**
	 * Shoots the given projectile from the given shooter towards target.
	 * Copied from {@link net.minecraft.entity.mob.SkeletonEntity#shootAt(LivingEntity, float)}
	 * @param shooter The entity shooting.
	 * @param projectile The projectile to shoot (we assume it is already in the correct position, so it is recommended to use the constructor taking an entity as an argument).
	 * @param target The target to shoot towards.
	 * @param sound The sound to play when shooting.
	 * @param speed The speed of the projectile.
	 * @param divergence The divergence, the higher this is, the less accurate the shot is.
	 */
	public static void shootProjectile(
			LivingEntity shooter, ProjectileEntity projectile, LivingEntity target,
			SoundEvent sound, float speed, float divergence
	) {
		double d = target.getX() - shooter.getX();
		double e = target.getBodyY(0.3333333333333333) - projectile.getY();
		double f = target.getZ() - shooter.getZ();
		double g = Math.sqrt(d * d + f * f);
		projectile.setVelocity(d, e + g * (double)0.2f, f, speed, divergence);
		shooter.playSound(sound, 1.0f, 1.0f / (shooter.getRandom().nextFloat() * 0.4f + 0.8f));
		shooter.getWorld().spawnEntity(projectile);
	}

}

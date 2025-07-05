package nu.metacraft.core.util.helper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public class EntityAIHelper {

	public static void shootProjectile(
			LivingEntity shooter, ProjectileEntity projectile, Entity target,
			SoundEvent sound, float speed, float divergence, float volume
	) {
		var direction = getDirection(projectile, target);
		projectile.setVelocity(direction.getX(), direction.getY(), direction.getZ(), speed, divergence);
		shooter.playSound(sound, volume, 1.0f / (shooter.getRandom().nextFloat() * 0.4f + 0.8f));
		shooter.getWorld().spawnEntity(projectile);
	}

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
			LivingEntity shooter, ProjectileEntity projectile, Entity target,
			SoundEvent sound, float speed, float divergence
	) {
		shootProjectile(shooter, projectile, target, sound, speed, divergence, 1.0f);
	}

	public static Vec3d getDirection(Entity projectile, Entity target) {
		double x = target.getX() - projectile.getX();
		double y = target.getBoundingBox().getCenter().getY() - projectile.getY();
		double z = target.getZ() - projectile.getZ();
		double g = Math.sqrt(x * x + z * z) * projectile.getFinalGravity() * 4;
		return new Vec3d(x, y + g, z);
	}

	public static Vec3d calculateVelocity(
			double x, double y, double z, float power, float uncertainty,
			Random random
	) {
		return new Vec3d(x, y, z).normalize().add(
				random.nextTriangular(0.0, 0.0172275 * (double)uncertainty),
				random.nextTriangular(0.0, 0.0172275 * (double)uncertainty),
				random.nextTriangular(0.0, 0.0172275 * (double)uncertainty)
		).multiply(power);
	}

}

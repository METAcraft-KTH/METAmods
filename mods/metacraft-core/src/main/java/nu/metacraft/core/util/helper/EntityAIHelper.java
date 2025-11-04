package nu.metacraft.core.util.helper;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public class EntityAIHelper {

	public static void shootProjectile(
			LivingEntity shooter, Projectile projectile, Entity target,
			SoundEvent sound, float speed, float divergence, float volume
	) {
		var direction = getDirection(projectile, target);
		projectile.shoot(direction.x(), direction.y(), direction.z(), speed, divergence);
		shooter.playSound(sound, volume, 1.0f / (shooter.getRandom().nextFloat() * 0.4f + 0.8f));
		shooter.level().addFreshEntity(projectile);
	}

	/**
	 * Shoots the given projectile from the given shooter towards target.
	 * Copied from {@link net.minecraft.world.entity.monster.Skeleton#performRangedAttack(LivingEntity, float)}
	 * @param shooter The entity shooting.
	 * @param projectile The projectile to shoot (we assume it is already in the correct position, so it is recommended to use the constructor taking an entity as an argument).
	 * @param target The target to shoot towards.
	 * @param sound The sound to play when shooting.
	 * @param speed The speed of the projectile.
	 * @param divergence The divergence, the higher this is, the less accurate the shot is.
	 */
	public static void shootProjectile(
			LivingEntity shooter, Projectile projectile, Entity target,
			SoundEvent sound, float speed, float divergence
	) {
		shootProjectile(shooter, projectile, target, sound, speed, divergence, 1.0f);
	}

	public static Vec3 getDirection(Entity projectile, Entity target) {
		double x = target.getX() - projectile.getX();
		double y = target.getBoundingBox().getCenter().y() - projectile.getY();
		double z = target.getZ() - projectile.getZ();
		double g = Math.sqrt(x * x + z * z) * projectile.getGravity() * 4;
		return new Vec3(x, y + g, z);
	}

	public static Vec3 calculateVelocity(
			double x, double y, double z, float power, float uncertainty,
			RandomSource random
	) {
		return new Vec3(x, y, z).normalize().add(
				random.triangle(0.0, 0.0172275 * (double)uncertainty),
				random.triangle(0.0, 0.0172275 * (double)uncertainty),
				random.triangle(0.0, 0.0172275 * (double)uncertainty)
		).scale(power);
	}

}

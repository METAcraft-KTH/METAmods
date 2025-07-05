package nu.metacraft.lib.util;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public class Particles {

	public static void spawnAngerParticles(Entity entity, Random random) {
		Box box = entity.getBoundingBox();
		Vec3d center = box.getCenter();
		((ServerWorld) entity.getWorld()).spawnParticles(
				ParticleTypes.ANGRY_VILLAGER,
				center.getX() + random.nextGaussian() * box.getLengthX() / 2,
				center.getY() + box.getLengthY()/4 + random.nextGaussian() * box.getLengthY()/4,
				center.getZ() + random.nextGaussian() * box.getLengthZ() / 2,
				1, 0, 0.1 + random.nextDouble() * box.getLengthY()/4, 0, 1
		);
	}

}

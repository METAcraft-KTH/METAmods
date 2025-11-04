package nu.metacraft.lib.util;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Particles {

	public static void spawnAngerParticles(Entity entity, RandomSource random) {
		AABB box = entity.getBoundingBox();
		Vec3 center = box.getCenter();
		((ServerLevel) entity.level()).sendParticles(
				ParticleTypes.ANGRY_VILLAGER,
				center.x() + random.nextGaussian() * box.getXsize() / 2,
				center.y() + box.getYsize()/4 + random.nextGaussian() * box.getYsize()/4,
				center.z() + random.nextGaussian() * box.getZsize() / 2,
				1, 0, 0.1 + random.nextDouble() * box.getYsize()/4, 0, 1
		);
	}

}

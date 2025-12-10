package nu.metacraft.bosses.extensions;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.phys.Vec3;

public interface OminousItemSpawnerExtension {

	default Entity metacraft_bosses$getSpawnOverride() {
		return null;
	}

	default Optional<Vec3> metacraft_bosses$getDirection(Projectile projectile, ProjectileItem.DispenseConfig settings) {
		return Optional.empty();
	}
}

package se.metacraft.bosses.extensions;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ProjectileItem;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public interface OminousItemSpawnerExtension {

	default Entity metacraft_bosses$getSpawnOverride() {
		return null;
	}

	default Optional<Vec3d> metacraft_bosses$getDirection(ProjectileEntity projectile, ProjectileItem.Settings settings) {
		return Optional.empty();
	}
}

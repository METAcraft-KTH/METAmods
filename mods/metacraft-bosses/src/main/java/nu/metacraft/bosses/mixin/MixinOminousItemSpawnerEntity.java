package nu.metacraft.bosses.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import nu.metacraft.bosses.extensions.OminousItemSpawnerExtension;

@Mixin(OminousItemSpawner.class)
public abstract class MixinOminousItemSpawnerEntity implements OminousItemSpawnerExtension {

	@WrapOperation(
		method = "spawnItem",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
		)
	)
	private boolean spawnItem(
			ServerLevel instance, Entity entity, Operation<Boolean> original, @Local LocalRef<Entity> spawnedEntity
	) {
		var override = metacraft_bosses$getSpawnOverride();
		if (override != null) {
			spawnedEntity.set(override);
			return true;
		}
		return original.call(instance, entity);
	}

	@WrapOperation(
			method = "spawnItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/OminousItemSpawner;spawnProjectile(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ProjectileItem;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/Entity;"
			)
	)
	private Entity spawnItem(OminousItemSpawner instance, ServerLevel world, ProjectileItem item, ItemStack stack, Operation<Entity> original) {
		var override = metacraft_bosses$getSpawnOverride();
		if (override != null) {
			return override;
		}
		return original.call(instance, world, item, stack);
	}

	@ModifyArgs(
			method = "spawnProjectile",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
			)
	)
	private void spawnProjectile(Args args, @Local ProjectileItem.DispenseConfig settings) {
		Vec3 direction = metacraft_bosses$getDirection(args.get(0), settings).orElse(new Vec3(args.get(3), args.get(4), args.get(5)));
		args.set(3, direction.x);
		args.set(4, direction.y);
		args.set(5, direction.z);
	}
}

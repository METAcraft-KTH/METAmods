package nu.metacraft.bosses.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.OminousItemSpawnerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ProjectileItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import nu.metacraft.bosses.extensions.OminousItemSpawnerExtension;

@Mixin(OminousItemSpawnerEntity.class)
public abstract class MixinOminousItemSpawnerEntity implements OminousItemSpawnerExtension {

	@WrapOperation(
		method = "spawnItem",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/world/ServerWorld;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
		)
	)
	private boolean spawnItem(
			ServerWorld instance, Entity entity, Operation<Boolean> original, @Local LocalRef<Entity> spawnedEntity
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
					target = "Lnet/minecraft/entity/OminousItemSpawnerEntity;spawnProjectile(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ProjectileItem;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/entity/Entity;"
			)
	)
	private Entity spawnItem(OminousItemSpawnerEntity instance, ServerWorld world, ProjectileItem item, ItemStack stack, Operation<Entity> original) {
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
					target = "Lnet/minecraft/entity/projectile/ProjectileEntity;spawnWithVelocity(Lnet/minecraft/entity/projectile/ProjectileEntity;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;DDDFF)Lnet/minecraft/entity/projectile/ProjectileEntity;"
			)
	)
	private void spawnProjectile(Args args, @Local ProjectileItem.Settings settings) {
		Vec3d direction = metacraft_bosses$getDirection(args.get(0), settings).orElse(new Vec3d(args.get(3), args.get(4), args.get(5)));
		args.set(3, direction.x);
		args.set(4, direction.y);
		args.set(5, direction.z);
	}
}

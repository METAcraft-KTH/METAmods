package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import nu.metacraft.better_pets.BetterPetsTeleportHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class MixinEntity {

	@Inject(
			method = "teleportCrossDimension",
			at = @At("HEAD")
	)
	public void collectPets(
			ServerWorld from, ServerWorld _to,
			TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.collectPets((Entity) (Object) this, leashed, pets);
	}

	@Inject(
			method = "teleportSameDimension",
			at = @At("HEAD")
	)
	public void collectPets(
			ServerWorld world, TeleportTarget teleportTarget,
			CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.collectPets((Entity) (Object) this, leashed, pets);
	}

	@Inject(
			method = "teleportCrossDimension",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/TeleportTarget$PostDimensionTransition;onTransition(Lnet/minecraft/entity/Entity;)V"
			)
	)
	public void teleportPets(
			ServerWorld from, ServerWorld _to,
			TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir,
			@Local Entity entity, //The new entity created at the destination teleport.
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.teleportPets(entity, leashed, pets);
	}

	@Inject(
			method = "teleportSameDimension",
			at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/TeleportTarget$PostDimensionTransition;onTransition(Lnet/minecraft/entity/Entity;)V"
			)
	)
	public void teleportPets(
			ServerWorld world, TeleportTarget teleportTarget,
			CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.teleportPets((Entity) (Object) this, leashed, pets);
	}
}

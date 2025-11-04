package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import nu.metacraft.better_pets.BetterPetsTeleportHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.portal.TeleportTransition;

@Mixin(Entity.class)
public abstract class EntityMixin {

	@Inject(
			method = "teleportCrossDimension",
			at = @At("HEAD")
	)
	public void collectPets(
			ServerLevel from, ServerLevel _to,
			TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir,
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
			ServerLevel world, TeleportTransition teleportTarget,
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
				target = "Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;onTransition(Lnet/minecraft/world/entity/Entity;)V"
			)
	)
	public void teleportPets(
			ServerLevel from, ServerLevel _to,
			TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir,
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
				target = "Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;onTransition(Lnet/minecraft/world/entity/Entity;)V"
			)
	)
	public void teleportPets(
			ServerLevel world, TeleportTransition teleportTarget,
			CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.teleportPets((Entity) (Object) this, leashed, pets);
	}
}

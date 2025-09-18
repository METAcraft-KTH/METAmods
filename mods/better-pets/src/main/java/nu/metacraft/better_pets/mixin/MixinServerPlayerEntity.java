package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import nu.metacraft.better_pets.BetterPetsTeleportHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity {

	public MixinServerPlayerEntity(World world, GameProfile profile) {
		super(world, profile);
	}

	@Inject(
			method = "teleportTo",
			at = @At("HEAD")
	)
	public void collectPets(
			TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.collectPets(this, leashed, pets);
	}

	@Inject(
		method = "teleportTo",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/TeleportTarget$PostDimensionTransition;onTransition(Lnet/minecraft/entity/Entity;)V"
		)
	)
	public void teleportPets(
			TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.teleportPets(this, leashed, pets);
	}
}

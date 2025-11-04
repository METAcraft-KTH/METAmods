package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.authlib.GameProfile;
import nu.metacraft.better_pets.BetterPetsTeleportHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerEntity extends Player {

	public MixinServerPlayerEntity(Level world, GameProfile profile) {
		super(world, profile);
	}

	@Inject(
			method = "teleport",
			at = @At("HEAD")
	)
	public void collectPets(
			TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.collectPets(this, leashed, pets);
	}

	@Inject(
		method = "teleport",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;onTransition(Lnet/minecraft/world/entity/Entity;)V"
		)
	)
	public void teleportPets(
			TeleportTransition teleportTarget, CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		BetterPetsTeleportHelper.teleportPets(this, leashed, pets);
	}
}

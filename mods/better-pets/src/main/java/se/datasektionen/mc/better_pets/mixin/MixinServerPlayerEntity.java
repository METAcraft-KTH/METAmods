package se.datasektionen.mc.better_pets.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.better_pets.TameableExtension;
import se.datasektionen.mc.metacraft_lib.util.helper.TeleportHelper;

import java.util.List;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity {

	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Shadow public abstract ServerWorld getServerWorld();

	@Inject(
			method = "teleportTo",
			at = @At("HEAD")
	)
	public void collectPets(
			TeleportTarget teleportTarget, CallbackInfoReturnable<Entity> cir,
			@Share("leashed") LocalRef<List<? extends Entity>> leashed,
			@Share("pets") LocalRef<List<? extends LivingEntity>> pets
	) {
		leashed.set(
				getServerWorld().getEntitiesByType(
						TypeFilter.instanceOf(Entity.class),
						entity -> entity instanceof Leashable leashable && leashable.isLeashed() && leashable.getLeashHolder() == this
				)
		);
		pets.set(getServerWorld().getEntitiesByType(
				TypeFilter.instanceOf(TameableEntity.class),
				entity ->
						((TameableExtension) entity).metacraft$getCurrentFollowTarget() == (Object) this
						&& !entity.cannotFollowOwner()
		));
		pets.get().forEach(pet -> {
			this.getServerWorld().getChunkManager().addTicket(
					TeleportHelper.TELEPORT_MOB_SOON, pet.getChunkPos(), 2
			);
		});
		leashed.get().forEach(pet -> {
			this.getServerWorld().getChunkManager().addTicket(
					TeleportHelper.TELEPORT_MOB_SOON, pet.getChunkPos(), 2
			);
		});
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
			@Share("pets") LocalRef<List<? extends TameableEntity>> pets
	) {
		leashed.get().forEach(l -> {
			((Leashable) l).detachLeashWithoutDrop();
			TeleportHelper.teleportEntityToPlayer(
					(ServerPlayerEntity) (Object) this, l,
					e -> ((Leashable) e).attachLeash(this, true)
			);
		});
		pets.get().forEach(pet -> {
			TeleportHelper.teleportEntityToPlayer((ServerPlayerEntity) (Object) this, pet);
		});
	}
}

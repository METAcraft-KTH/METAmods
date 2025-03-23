package se.datasektionen.mc.better_pets.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.better_pets.TameableExtension;
import se.datasektionen.mc.metacraft_lib.util.helper.TamedHelper;

@Mixin(EndPortalBlock.class)
public class MixinEndPortalBlock {

	@Inject(
		method = "createTeleportTarget",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;getWorldSpawnPos(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"
		),
		cancellable = true
	)
	public void createTeleportTarget(
			ServerWorld world, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTarget> cir
	) {
		if (entity instanceof EnderPearlEntity) return; //If people throw an ender pearl through the portal, they probably want to get to world spawn.
		TamedHelper.getRelevantPlayer(entity).ifPresent(playerID -> {
			var player = world.getServer().getPlayerManager().getPlayer(playerID);
			if (player != null) {
				cir.setReturnValue(player.getRespawnTarget(true, TeleportTarget.ADD_PORTAL_CHUNK_TICKET));
			}
		});
	}

	@Inject(
		method = "onEntityCollision",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;detachForDimensionChange()V"
		)
	)
	public void teleportPetsFirstTime(
			BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, CallbackInfo ci, @Local ServerPlayerEntity player
	) {
		var target = player.getRespawnTarget(true, TeleportTarget.ADD_PORTAL_CHUNK_TICKET);
		player.getServerWorld().getEntitiesByType(
				TypeFilter.instanceOf(TameableEntity.class),
				e ->    ((TameableExtension) e).metacraft$getCurrentFollowTarget() == player
						&& !e.cannotFollowOwner()
		).forEach(pet -> {
			pet.teleportTo(target);
		});
	}

}

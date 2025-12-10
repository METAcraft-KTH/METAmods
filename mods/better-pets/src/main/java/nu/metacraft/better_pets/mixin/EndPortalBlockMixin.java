package nu.metacraft.better_pets.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.better_pets.TameableExtension;
import nu.metacraft.lib.util.helper.TamedHelper;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

	@Inject(
		method = "getPortalDestination",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;adjustSpawnLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"
		),
		cancellable = true
	)
	public void createTeleportTarget(
			ServerLevel world, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir
	) {
		if (entity instanceof ThrownEnderpearl) return; //If people throw an ender pearl through the portal, they probably want to get to world spawn.
		TamedHelper.getRelevantPlayer(entity).ifPresent(playerID -> {
			var player = world.getServer().getPlayerList().getPlayer(playerID);
			if (player != null) {
				cir.setReturnValue(player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.PLACE_PORTAL_TICKET));
			}
		});
	}

	@Inject(
		method = "entityInside",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;showEndCredits()V"
		)
	)
	public void teleportPetsFirstTime(
			BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier handler, boolean bl, CallbackInfo ci, @Local ServerPlayer player
	) {
		var target = player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.PLACE_PORTAL_TICKET);
		player.level().getEntities(
				EntityTypeTest.forClass(TamableAnimal.class),
				e ->    ((TameableExtension) e).metacraft$getCurrentFollowTarget() == player
						&& !e.unableToMoveToOwner()
		).forEach(pet -> {
			pet.teleport(target);
		});
	}

}

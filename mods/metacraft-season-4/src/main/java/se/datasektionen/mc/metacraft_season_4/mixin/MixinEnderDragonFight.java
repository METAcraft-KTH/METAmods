package se.datasektionen.mc.metacraft_season_4.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_season_4.end.EndBossPlayerState;

import java.util.List;

@Mixin(EnderDragonFight.class)
public class MixinEnderDragonFight {

	@Shadow @Final private ServerWorld world;

	@Shadow @Final private BlockPos origin;

	@Inject(method = "respawnDragon(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
	public void respawnDragon(List<EndCrystalEntity> crystals, CallbackInfo ci) {
		EndBossPlayerState.getInstance(world).ifPresent(state -> {
			double dist = Math.pow(state.getConfig().maxDistanceFromSpawn(), 2);
			if (state.hasBoss() && state.getPlayerSpawnPos().squaredDistanceTo(origin.getX(), origin.getY(), origin.getZ()) <= dist) {
				crystals.forEach(c -> {
					world.createExplosion(c, c.getX(), c.getY(), c.getZ(), 6.0F, World.ExplosionSourceType.NONE);
					c.discard();
				});
				world.getPlayers(p -> p.getPos().squaredDistanceTo(origin.getX(), origin.getY(), origin.getZ()) <= dist).forEach(
						player -> player.sendMessage(Text.literal("The powerful aura of ").append(state.getBossName()).append(" makes it impossible to respawn the ender dragon!"), true)
				);
				ci.cancel();
			}
		});
	}
	
}

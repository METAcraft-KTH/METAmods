package se.metacraft.player_bosses.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.player_bosses.state.PlayerBossState;

import java.util.List;

@Mixin(EnderDragonFight.class)
public class EnderDragonFightMixin {

	@Shadow private ServerLevel level;

	@Shadow private BlockPos origin;

	@Inject(method = "respawnDragon(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
	public void respawnDragon(List<EndCrystal> crystals, CallbackInfo ci) {
		PlayerBossState.getInstance(level).ifPresent(state -> {
			double dist = Math.pow(state.getConfig().maxDistanceFromSpawn(), 2);
			if (state.hasBoss() && state.getPlayerSpawnPos().distanceToSqr(origin.getX(), origin.getY(), origin.getZ()) <= dist) {
				crystals.forEach(c -> {
					level.explode(c, c.getX(), c.getY(), c.getZ(), 6.0F, Level.ExplosionInteraction.NONE);
					c.discard();
				});
				level.getPlayers(p -> p.position().distanceToSqr(origin.getX(), origin.getY(), origin.getZ()) <= dist).forEach(
					player -> player.sendOverlayMessage(Component.literal("The powerful aura of ").append(state.getBossName()).append(" makes it impossible to respawn the ender dragon!"))
				);
				ci.cancel();
			}
		});
	}

}

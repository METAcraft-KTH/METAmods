package se.datasektionen.mc.metacraft_season_4.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_season_4.end.EndBossPlayerState;

import java.util.List;

@Mixin(EnderDragonFight.class)
public abstract class MixinEnderDragonFight {

	@Shadow @Final private ServerWorld world;

	@Shadow @Final private BlockPos origin;

	@Shadow protected abstract void generateNewEndGateway();

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

	@WrapWithCondition(
		method = "dragonKilled",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/dragon/EnderDragonFight;generateNewEndGateway()V")
	)
	public boolean mcmakisteinImpossibleDragonFix(EnderDragonFight instance, @Local(argsOnly = true) EnderDragonEntity dragon) {
		var scoreboard = dragon.getWorld().getScoreboard();
		var dragonSetup = scoreboard.getNullableObjective("mcm.its.dragon.setup.state");
		int state = 0;
		if (dragonSetup != null) {
			var s = scoreboard.getScore(ScoreHolder.fromName("#global"), dragonSetup);
			if (s != null) {
				state = s.getScore();
			}
		}
		return state != 1;
	}

	@Unique
	private Entity mcmakisteinDragon;

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (mcmakisteinDragon == null) {
			var list = world.getEntitiesByType(
					TypeFilter.instanceOf(DisplayEntity.class),
					(e) -> e.getCommandTags().contains("aj.impossible_dragon.root")
			);
			if (!list.isEmpty()) {
				mcmakisteinDragon = list.getFirst();
			}
		} else {
			if (mcmakisteinDragon.isRemoved() && mcmakisteinDragon.getRemovalReason().shouldDestroy()) {
				mcmakisteinDragon = null;
				generateNewEndGateway();
			}
		}
	}
	
}

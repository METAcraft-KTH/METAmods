package nu.metacraft.season_5.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.scores.ScoreHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightMixin {

	@Shadow
	protected abstract void spawnNewGateway();

	@Shadow
	@Final
	private ServerLevel level;

	@WrapWithCondition(
			method = "setDragonKilled",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/end/EnderDragonFight;spawnNewGateway()V")
	)
	public boolean mcmakisteinImpossibleDragonFix(EnderDragonFight instance, @Local(argsOnly = true, name = "dragon") EnderDragon dragon) {
		var scoreboard = dragon.level().getScoreboard();
		var dragonSetup = scoreboard.getObjective("mcm.its.dragon.setup.state");
		int state = 0;
		if (dragonSetup != null) {
			var s = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly("#global"), dragonSetup);
			if (s != null) {
				state = s.value();
			}
		}
		return state != 1;
	}

	@Unique
	private Entity mcmakisteinDragon;

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (mcmakisteinDragon == null) {
			var list = level.getEntities(
					EntityTypeTest.forClass(Display.class),
					(e) -> e.entityTags().contains("aj.impossible_dragon.root")
			);
			if (!list.isEmpty()) {
				mcmakisteinDragon = list.getFirst();
			}
		} else {
			if (mcmakisteinDragon.isRemoved() && mcmakisteinDragon.getRemovalReason().shouldDestroy()) {
				mcmakisteinDragon = null;
				spawnNewGateway();
			}
		}
	}

}

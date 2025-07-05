package nu.metacraft.season_4.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.season_4.end.EndBossPlayerState;
import nu.metacraft.season_4.end.EndData;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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

	@WrapWithCondition(
		method = "generateEndGateway",
		at = @At(
				value = "INVOKE",
				target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
		)
	)
	private <T> boolean generateEndGateway(
			Optional<T> instance, Consumer<? super @NotNull T> action, @Local(argsOnly = true) BlockPos pos
	) {
		var poolElements = world.getRegistryManager().getOrThrow(RegistryKeys.TEMPLATE_POOL);
		var gateway = poolElements.get(EndData.END_GATEWAY_DRAGON);
		if (gateway == null) {
			return true;
		} else {
			var rot = BlockRotation.values()[world.getRandom().nextInt(BlockRotation.values().length)];
			var element = gateway.getRandomElement(world.getRandom());
			var calcBox = element.getBoundingBox(world.getStructureTemplateManager(), BlockPos.ORIGIN, rot);
			var actualPos = pos.subtract(calcBox.getCenter());
			element.generate(
					world.getStructureTemplateManager(), world, world.getStructureAccessor(),
					world.getChunkManager().getChunkGenerator(), actualPos, actualPos,
					rot, BlockBox.infinite(), world.getRandom(),
					StructureLiquidSettings.APPLY_WATERLOGGING, false
			);
			return false;
		}
	}
	
}

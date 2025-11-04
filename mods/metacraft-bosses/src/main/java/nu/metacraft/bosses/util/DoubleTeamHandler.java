package nu.metacraft.bosses.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.entity.METAcraftEntities;
import nu.metacraft.core.extensions.EntityExtensions;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.extensions.LivingEntityExtensions;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public record DoubleTeamHandler(
		LivingEntity primary, Settings settings,
		int time, int spawnsSoFar
) implements SpawnGroupData {

	public static Codec<DoubleTeamHandler> getCodec(LivingEntity owner) {
		return getMapCodec(owner).codec();
	}

	public static MapCodec<DoubleTeamHandler> getMapCodec(LivingEntity owner) {
		return RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				Settings.CODEC.forGetter(DoubleTeamHandler::settings),
				Codec.INT.fieldOf("time").forGetter(DoubleTeamHandler::time),
				Codec.INT.fieldOf("splits_so_far").forGetter(DoubleTeamHandler::spawnsSoFar)
			).apply(instance,
				(settings, time, splitsSoFar) -> new DoubleTeamHandler(
						owner, settings, time, splitsSoFar
				)
			)
		);
	}

	public DoubleTeamHandler(
			LivingEntity primary, IntProvider delay, int maxSpawns, FloatProvider dist, double handoverChance
	) {
		this(primary, new Settings(delay, maxSpawns, dist, handoverChance, new CompoundTag(), Optional.empty()), 0, 0);
	}

	public static void applyToEntity(DoubleTeamHandler handler) {
		((LivingEntityExtensions) handler.primary).metacraft$setDoubleTeamHandler(handler);
	}

	private void setCloneData(LivingEntity clone, ProblemReporter logger) {
		((LivingEntityExtensions) clone).metacraft$setPhantomEntity(true);
		((LivingEntityExtensions) clone).metacraft$setDoubleTeamHandler(null);
		if (primary.getTeam() != null) {
			clone.level().getScoreboard().addPlayerToTeam(
					clone.getScoreboardName(), primary.getTeam()
			);
		}
		((LivingEntityExtensions) clone).metacraft$setSoulboundEntity(
				((LivingEntityExtensions) primary).metacraft$getNonSoulboundMaster()
		);
		((EntityExtensions) clone).metacraft_lib$setBossBar(null);
		if (settings.initializeClone.orElse(settings.dataToApply.isEmpty())) {
			if (clone instanceof Mob mob) {
				mob.finalizeSpawn(
						(ServerLevelAccessor) clone.level(), clone.level().getCurrentDifficultyAt(clone.blockPosition()),
						EntitySpawnReason.REINFORCEMENT, this
				);
			}
		}
		if (!settings.dataToApply.isEmpty()) {
			var l = logger.forChild(() -> "metacraft:DoubleTeamHandler#setCloneData");
			var writeView = TagValueOutput.createWithContext(l, clone.registryAccess());
			clone.saveWithoutId(writeView);
			var data = writeView.buildResult();
			data.merge(settings.dataToApply);
			var readView = TagValueInput.create(l, clone.registryAccess(), data);
			clone.load(readView);
		}
	}

	private LivingEntity createClone() {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:DoubleTeamHandler#craeteClone", METAcraftBosses.LOGGER)) {
			var writeView = TagValueOutput.createWithContext(logging, primary.registryAccess());
			primary.saveWithoutId(writeView);
			var data = writeView.buildResult();
			if (primary instanceof Player) {
				var player = METAcraftEntities.PLAYER.create(primary.level(), EntitySpawnReason.REINFORCEMENT);
				player.copyFromPlayerData(data);
				setCloneData(player, logging);
				return player;
			} else {
				var clone = (LivingEntity) primary.getType().create(primary.level(), EntitySpawnReason.REINFORCEMENT);
				data.remove("UUID");
				var readView = TagValueInput.create(logging, primary.registryAccess(), data);
				clone.load(readView);
				setCloneData(clone, logging);
				return clone;
			}
		}
	}

	private void handleTick(LivingEntity clone) {
		primary.level().addFreshEntity(clone);
		var angle = primary.getRandom().nextFloat() * 360;
		var angleUp = primary.getRandom().nextFloat() * 180;
		angleUp -= angleUp/2;
		var facing = Vec3.directionFromRotation(angleUp, angle);
		var target = primary.position().add(facing.scale(
				settings.distance.sample(primary.getRandom())
		));
		var pos = new BlockPos.MutableBlockPos();
		pos.set(target.x, target.y, target.z);

		int upDist = Mth.ceil(settings.distance.sample(primary.getRandom()));
		for (int i = 0; i < upDist; i++) {
			if (!primary.level().getBlockState(pos).blocksMotion()) {
				if (i > 0) {
					target = target.add(0, i, 0);
				}
				break;
			}
			pos.move(Direction.UP);
		}

		primary.randomTeleport(target.x, target.y, target.z, false);
	}

	public DoubleTeamHandler tick() {
		if (isDone()) {
			return this;
		}
		if (time == 0) {
			var clone = createClone();
			if (primary.getRandom().nextDouble() < settings.passToCloneChance) {
				applyToEntity(nextSpawnFor(clone));
				handleTick(clone);
				return completed();
			}
			handleTick(clone);
			return nextSpawn();
		}

		return next();
	}

	public boolean isDone() {
		return spawnsSoFar >= settings.maxSplits;
	}

	private int getNextTick() {
		int nextTick = time+1;
		if (nextTick >= settings.delay.sample(primary.getRandom())) {
			nextTick = 0;
		}
		return nextTick;
	}

	public DoubleTeamHandler next() {
		return new DoubleTeamHandler(primary, settings, getNextTick(), spawnsSoFar);
	}

	public DoubleTeamHandler nextSpawn() {
		return new DoubleTeamHandler(primary, settings, getNextTick(), spawnsSoFar+1);
	}

	public DoubleTeamHandler completed() {
		return new DoubleTeamHandler(primary, settings, 0, settings().maxSplits);
	}

	public DoubleTeamHandler nextSpawnFor(LivingEntity entity) {
		return new DoubleTeamHandler(entity, settings,  getNextTick(), spawnsSoFar+1);
	}

	public record Settings(
			IntProvider delay, int maxSplits,
			FloatProvider distance, double passToCloneChance,
			CompoundTag dataToApply, Optional<Boolean> initializeClone
	) {
		public static final MapCodec<Settings> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				IntProvider.POSITIVE_CODEC.fieldOf("delay").forGetter(Settings::delay),
				Codec.INT.fieldOf("max_splits").forGetter(Settings::maxSplits),
				FloatProvider.CODEC.fieldOf("distance").forGetter(Settings::distance),
				Codec.DOUBLE.fieldOf("pass_to_clone_chance").forGetter(Settings::passToCloneChance),
				CompoundTag.CODEC.optionalFieldOf("data_to_apply", new CompoundTag()).forGetter(Settings::dataToApply),
				Codec.BOOL.optionalFieldOf("initialize_clone").forGetter(Settings::initializeClone)
			).apply(instance, Settings::new)
		);
	}

}

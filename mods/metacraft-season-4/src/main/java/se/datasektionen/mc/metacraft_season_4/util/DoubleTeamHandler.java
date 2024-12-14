package se.datasektionen.mc.metacraft_season_4.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.FloatProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import se.datasektionen.mc.metacraft_core.entity.METAcraftEntities;
import se.datasektionen.mc.metacraft_core.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_season_4.extensions.LivingEntityExtensions;

public record DoubleTeamHandler(
		LivingEntity primary, Settings settings,
		int time, int spawnsSoFar
) {

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
		this(primary, new Settings(primary.getPos(), delay, maxSpawns, dist, handoverChance), 0, 0);
	}

	public static void applyToEntity(DoubleTeamHandler handler) {
		((LivingEntityExtensions) handler.primary).metacraft$setDoubleTeamHandler(handler);
	}

	private void setCloneData(LivingEntity clone) {
		((LivingEntityExtensions) clone).metacraft$setPhantomEntity(true);
		((LivingEntityExtensions) clone).metacraft$setDoubleTeamHandler(null);
		((LivingEntityExtensions) clone).metacraft$setSoulboundEntity(
				((LivingEntityExtensions) primary).metacraft$getNonSoulboundMaster()
		);
		((EntityExtensions) clone).metacraft_lib$setBossBar(null);
	}

	private LivingEntity createClone() {
		var data = primary.writeNbt(new NbtCompound());
		if (primary instanceof PlayerEntity) {
			var player = METAcraftEntities.PLAYER.create(primary.getWorld(), SpawnReason.REINFORCEMENT);
			player.copyFromPlayerData(data);
			setCloneData(player);
			return player;
		} else {
			var clone = (LivingEntity) primary.getType().create(primary.getWorld(), SpawnReason.REINFORCEMENT);
			data.remove("UUID");
			clone.readNbt(data);
			setCloneData(clone);
			return clone;
		}
	}

	private void handleTick(LivingEntity clone) {
		primary.getWorld().spawnEntity(clone);
		var angle = primary.getRandom().nextFloat() * 360;
		var angleUp = primary.getRandom().nextFloat() * 180;
		angleUp -= angleUp/2;
		var facing = Vec3d.fromPolar(angleUp, angle);
		var target = settings.center.add(facing.multiply(
				settings.distance.get(primary.getRandom())
		));
		var pos = new BlockPos.Mutable();
		pos.set(target.x, target.y, target.z);

		int upDist = MathHelper.ceil(settings.distance.get(primary.getRandom()));
		for (int i = 0; i < upDist; i++) {
			if (!primary.getWorld().getBlockState(pos).blocksMovement()) {
				if (i > 0) {
					target = target.add(0, i, 0);
				}
				break;
			}
			pos.move(Direction.UP);
		}

		primary.teleport(target.x, target.y, target.z, false);
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
		if (nextTick >= settings.delay.get(primary.getRandom())) {
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
			Vec3d center, IntProvider delay, int maxSplits,
			FloatProvider distance, double passToCloneChance
	) {
		public static final MapCodec<Settings> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				Vec3d.CODEC.fieldOf("center").forGetter(Settings::center),
				IntProvider.POSITIVE_CODEC.fieldOf("delay").forGetter(Settings::delay),
				Codec.INT.fieldOf("max_splits").forGetter(Settings::maxSplits),
				FloatProvider.VALUE_CODEC.fieldOf("distance").forGetter(Settings::distance),
				Codec.DOUBLE.fieldOf("pass_to_clone_chance").forGetter(Settings::passToCloneChance)
			).apply(instance, Settings::new)
		);
	}

}

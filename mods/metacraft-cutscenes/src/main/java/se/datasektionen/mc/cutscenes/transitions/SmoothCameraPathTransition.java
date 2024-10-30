package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.extension.ServerPlayerEntityExtensions;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.RelativeSmoothCameraPathConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TimestampedSmoothCameraPathConfig;
import se.datasektionen.mc.cutscenes.util.InterpolationSet;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.util.Target;
import se.datasektionen.mc.cutscenes.util.TimestampedInterpolationSet;

import java.util.Optional;
import java.util.Set;
import java.util.TimerTask;

public class SmoothCameraPathTransition implements Transition, DeltaTickTransition {

	public static final MapCodec<SmoothCameraPathTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					RelativeSmoothCameraPathConfig.INTERPOLATION_SET_CODEC.optionalFieldOf("targets").forGetter(t -> t.config),
					TimestampedSmoothCameraPathConfig.INTERPOLATION_SET_CODEC.optionalFieldOf("unparsed_targets").forGetter(t -> t.timestampedConfig),
					Codec.LONG.fieldOf("progress").forGetter(t -> t.progress)
			).apply(instance, SmoothCameraPathTransition::new)
	);

	private Optional<InterpolationSet<Target>> config = Optional.empty();
	private Optional<TimestampedInterpolationSet<Target>> timestampedConfig = Optional.empty();

	private TimerTask task;
	private long progress;

	public SmoothCameraPathTransition(RelativeSmoothCameraPathConfig config) {
		this.config = Optional.of(config.targets());
	}

	public SmoothCameraPathTransition(TimestampedSmoothCameraPathConfig timestampedConfig) {
		this.timestampedConfig = Optional.of(timestampedConfig.targets());
	}

	public SmoothCameraPathTransition(
			Optional<InterpolationSet<Target>> config,
			Optional<TimestampedInterpolationSet<Target>> timestampedConfig,
			long progress
	) {
		this.config = config;
		this.timestampedConfig = timestampedConfig;
		this.progress = progress;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (config.isEmpty() && timestampedConfig.isPresent()) {
			config = timestampedConfig.map(
					t -> t.createFromRange(interval.getStart(), interval.getEnd())
			);
		}
		config.ifPresent(
				c -> {
					var target = cutscene.copyPlayers().stream().findAny().map(Target::fromPlayer).orElse(
							new Target(Vec3d.ZERO, 0, 0)
					);
					c.setStartIfNotPresent(target);
					c.setEndIfNotPresent(target);
				}
		);
	}

	@Override
	public void activate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		((ServerPlayerEntityExtensions) player).metacraft$setAllowWrongMovements(true);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			boolean send = false;
			if (!player.getAbilities().allowFlying) {
				player.getAbilities().allowFlying = true;
				send = true;
			}
			if (!player.getAbilities().flying) {
				player.getAbilities().flying = true;
				send = true;
			}
			if (send) {
				player.sendAbilitiesUpdate();
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		player.interactionManager.getGameMode().setAbilities(player.getAbilities());
		player.sendAbilitiesUpdate();
		((ServerPlayerEntityExtensions) player).metacraft$setAllowWrongMovements(false);
	}

	@Override
	public void setProgress(long time) {
		this.progress = time;
	}

	@Override
	public long getProgress() {
		return progress;
	}

	private int prevTick = 0;

	@Override
	public void tickDelta(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval, float delta) {
		config.ifPresent(config -> {
			var target = config.interpolate(delta);

			cutscene.forAllPlayers(player -> {
				//We don't use requestTeleport because it's not thread safe.
				player.networkHandler.sendPacket(new PlayerPositionLookS2CPacket(
						target.pos().x, target.pos().y, target.pos().z, target.yaw(), target.pitch(),
						Set.of(), -1 //Ignores the teleport confirm packet.
				));
				if (prevTick != cutscene.getCurrentTime()) {
					player.getServer().execute(() -> {
						player.updatePositionAndAngles(
								target.pos().x, target.pos().y, target.pos().z, target.yaw(), target.pitch()
						);
					});
				}
			});
			prevTick = cutscene.getCurrentTime();
		});
	}

	@Override
	public void setTask(TimerTask task) {
		this.task = task;
	}

	@Override
	public TimerTask getTask() {
		return task;
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.CAMERAE_PATH;
	}
}

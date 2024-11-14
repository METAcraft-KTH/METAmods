package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.RelativeSmoothCameraPathConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TimestampedSmoothCameraPathConfig;
import se.datasektionen.mc.cutscenes.util.InterpolationSet;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.util.Target;
import se.datasektionen.mc.cutscenes.util.TimestampedInterpolationSet;

import java.util.Optional;

public class SmoothCameraPathTransition implements Transition {

	public static final MapCodec<SmoothCameraPathTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					RelativeSmoothCameraPathConfig.INTERPOLATION_SET_CODEC.optionalFieldOf("targets").forGetter(t -> t.config),
					TimestampedSmoothCameraPathConfig.INTERPOLATION_SET_CODEC.optionalFieldOf("unparsed_targets").forGetter(t -> t.timestampedConfig),
					CommonConfig.CODEC.forGetter(c -> c.commonConfig)
			).apply(instance, SmoothCameraPathTransition::new)
	);

	private Optional<InterpolationSet<Target>> config = Optional.empty();
	private Optional<TimestampedInterpolationSet<Target>> timestampedConfig = Optional.empty();
	private final CommonConfig commonConfig;

	public SmoothCameraPathTransition(RelativeSmoothCameraPathConfig config) {
		this.config = Optional.of(config.targets());
		this.commonConfig = config.commonConfig();
	}

	public SmoothCameraPathTransition(TimestampedSmoothCameraPathConfig timestampedConfig) {
		this.timestampedConfig = Optional.of(timestampedConfig.targets());
		this.commonConfig = timestampedConfig.commonConfig();
	}

	public SmoothCameraPathTransition(
			Optional<InterpolationSet<Target>> config,
			Optional<TimestampedInterpolationSet<Target>> timestampedConfig,
			CommonConfig commonConfig
	) {
		this.config = config;
		this.timestampedConfig = timestampedConfig;
		this.commonConfig = commonConfig;
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

	private static final String MARKER_ID = "metacraft$smooth_camera_marker";

	private void setLinearInterpolationDuration(Entity display, int duration) {
		var data = display.writeNbt(new NbtCompound());
		data.putInt(DisplayEntity.TELEPORT_DURATION_KEY, duration);
		display.readNbt(data);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			cutscene.getRootEntity(MARKER_ID).ifPresentOrElse(marker -> {
				if (player.isSpectator()) {
					player.setCameraEntity(marker);
				}
			}, () -> {
				var display = EntityType.TEXT_DISPLAY.create(cutscene.getCutsceneWorld(), SpawnReason.TRIGGERED);
				setLinearInterpolationDuration(display, commonConfig.interpolationDuration);
				cutscene.addEntity(MARKER_ID, display);
			});
		});
		config.ifPresent(config -> {
			int currentTimeAdjusted = cutscene.getCurrentTime() + commonConfig.interpolationDuration;
			if (currentTimeAdjusted > interval.getEnd()) {
				cutscene.getRootEntity(MARKER_ID).ifPresent(marker -> {
					setLinearInterpolationDuration(marker, commonConfig.interpolationDuration - (currentTimeAdjusted - interval.getEnd()));
				});
				return;
			}
			double delta = ((double) currentTimeAdjusted - interval.getStart()) / interval.getLength();
			var target = config.interpolate(delta);
			cutscene.getRootEntity(MARKER_ID).ifPresent(entity -> {
				if ((cutscene.getCurrentTime() - interval.getStart()) % commonConfig.teleportInterval == 0) {
					entity.updatePositionAndAngles(
							target.pos().x, target.pos().y + EntityType.PLAYER.getDimensions().eyeHeight(), target.pos().z, target.yaw(), target.pitch()
					);
				}
			});
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (player.isSpectator()) {
			player.setCameraEntity(null);
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.CAMERAE_PATH;
	}

	public record CommonConfig(int interpolationDuration, int teleportInterval) {
		public static final MapCodec<CommonConfig> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Codecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(CommonConfig::interpolationDuration),
						Codecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(CommonConfig::teleportInterval)
				).apply(instance, CommonConfig::new)
		);
	}
}

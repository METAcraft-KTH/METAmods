package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.extension.EntityExtension;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.SmoothCameraPathConfig;
import se.datasektionen.mc.cutscenes.util.CutsceneContext;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.util.Target;
import se.datasektionen.mc.metacraft_lib.util.error_reporters.LoggingErrorReporter;

public class SmoothCameraPathTransition implements Transition {

	public static final MapCodec<SmoothCameraPathTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SmoothCameraPathConfig.CODEC.forGetter(t -> t.config)
			).apply(instance, SmoothCameraPathTransition::new)
	);

	private final SmoothCameraPathConfig config;
	private InterpolationSet<CutsceneContext, Target> interpolationSet;

	public SmoothCameraPathTransition(SmoothCameraPathConfig config) {
		this.config = config;
	}

	private void init(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		interpolationSet = config.targets().getTargets(interval);
		var target = cutscene.getPlayers().stream().findAny().map(Target::fromEntity).orElse(
				Target.DEFAULT
		);
		interpolationSet = interpolationSet.setStartIfNotPresent(target);
		interpolationSet = interpolationSet.setEndIfNotPresent(target);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		init(cutscene, interval);
	}

	private static final String MARKER_ID = "metacraft$smooth_camera_marker";

	private void setLinearInterpolationDuration(Entity display, int duration) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothCameraPathTransition#setLinearInterpolationDuration", Cutscenes.LOGGER)) {
			var writeView = NbtWriteView.create(logging, display.getRegistryManager());
			display.writeData(writeView);
			var data = writeView.getNbt();
			data.putInt(DisplayEntity.TELEPORT_DURATION_KEY, duration);
			var readView = NbtReadView.create(logging, display.getRegistryManager(), data);
			display.readData(readView);
		}
	}

	private int getAdjustedTime(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (cutscene.getCurrentTime() == interval.getStart()) {
			return cutscene.getCurrentTime();
		}
		return cutscene.getCurrentTime() + config.interpolationDuration();
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (interpolationSet == null) {
			init(cutscene, interval);
		}
		cutscene.forAllPlayers(player -> {
			cutscene.getRootEntity(MARKER_ID).ifPresentOrElse(marker -> {
				if (player.isSpectator()) {
					player.setCameraEntity(marker);
				}
			}, () -> {
				var display = EntityType.TEXT_DISPLAY.create(cutscene.getCutsceneWorld(), SpawnReason.TRIGGERED);
				setLinearInterpolationDuration(display, config.interpolationDuration());
				var target = interpolationSet.interpolate(0);
				display.updatePositionAndAngles(
						target.pos().x, target.pos().y + EntityType.PLAYER.getDimensions().eyeHeight(), target.pos().z, target.yaw(), target.pitch()
				);
				((EntityExtension) display).metacraft$setHasAccurateMovement(true);
				cutscene.addEntity(MARKER_ID, display);
			});
		});
		int currentTimeAdjusted = getAdjustedTime(cutscene, interval);
		if (currentTimeAdjusted > interval.getEnd()) {
			cutscene.getRootEntity(MARKER_ID).ifPresent(marker -> {
				setLinearInterpolationDuration(marker, config.interpolationDuration() - (currentTimeAdjusted - interval.getEnd()));
			});
			return;
		}
		double delta = ((double) currentTimeAdjusted - interval.getStart()) / interval.getLength();
		var target = interpolationSet.interpolate(delta);
		cutscene.getRootEntity(MARKER_ID).ifPresent(entity -> {
			if ((cutscene.getCurrentTime() - interval.getStart()) % config.teleportInterval() == 0) {
				entity.updatePositionAndAngles(
						target.pos().x, target.pos().y + EntityType.PLAYER.getDimensions().eyeHeight(), target.pos().z, target.yaw(), target.pitch()
				);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.getEntities(MARKER_ID).forEach(Entity::discard);
	}

	@Override
	public void deactivate(ServerPlayerEntity player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (player.isSpectator()) {
			player.setCameraEntity(null);
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.CAMERA_PATH;
	}

}

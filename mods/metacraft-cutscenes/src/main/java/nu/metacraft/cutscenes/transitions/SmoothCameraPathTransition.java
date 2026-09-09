package nu.metacraft.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.extension.EntityExtension;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.SmoothCameraPathConfig;
import nu.metacraft.cutscenes.util.CutsceneContext;
import nu.metacraft.core.util.InterpolationSet;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.util.Target;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

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
			var writeView = TagValueOutput.createWithContext(logging, display.registryAccess());
			display.saveWithoutId(writeView);
			var data = writeView.buildResult();
			data.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, duration);
			var readView = TagValueInput.create(logging, display.registryAccess(), data);
			display.load(readView);
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
					player.setCamera(marker);
				}
			}, () -> {
				var display = EntityTypes.TEXT_DISPLAY.create(cutscene.getCutsceneWorld(), EntitySpawnReason.TRIGGERED);
				setLinearInterpolationDuration(display, config.interpolationDuration());
				var target = interpolationSet.interpolate(0);
				display.absSnapTo(
						target.pos().x, target.pos().y + EntityTypes.PLAYER.getDimensions().eyeHeight(), target.pos().z, target.yaw(), target.pitch()
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
				entity.absSnapTo(
						target.pos().x, target.pos().y + EntityTypes.PLAYER.getDimensions().eyeHeight(), target.pos().z, target.yaw(), target.pitch()
				);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.getEntities(MARKER_ID).forEach(Entity::discard);
	}

	@Override
	public void deactivate(ServerPlayer player, CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (player.isSpectator()) {
			player.setCamera(null);
		}
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.CAMERA_PATH;
	}

}

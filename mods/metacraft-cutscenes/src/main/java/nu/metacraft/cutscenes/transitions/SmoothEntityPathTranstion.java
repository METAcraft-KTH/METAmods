package nu.metacraft.cutscenes.transitions;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.config.SmoothEntityPathConfig;
import nu.metacraft.cutscenes.util.CutsceneContext;
import nu.metacraft.core.util.InterpolationSet;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;

public class SmoothEntityPathTranstion implements Transition {

	public static final MapCodec<SmoothEntityPathTranstion> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SmoothEntityPathConfig.CODEC.forGetter(t -> t.config)
			).apply(instance, SmoothEntityPathTranstion::new)
	);

	private final SmoothEntityPathConfig config;
	private InterpolationSet<CutsceneContext, SmoothEntityPathConfig.DisplayEntityTarget> interpolationSet;

	public SmoothEntityPathTranstion(SmoothEntityPathConfig config) {
		this.config = config;
	}

	private void init(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		interpolationSet = config.targets().getTargets(interval);
		var target = config.entity().get(cutscene.getRefContext()).findAny().map(
				SmoothEntityPathConfig.DisplayEntityTarget::fromEntity
		).orElse(
				SmoothEntityPathConfig.DisplayEntityTarget.DEFAULT
		);
		interpolationSet = interpolationSet.setStartIfNotPresent(target);
		interpolationSet = interpolationSet.setEndIfNotPresent(target);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		init(cutscene, interval);
		config.entity().get(cutscene.getRefContext()).forEach(entity -> {
			setData(entity, interpolationSet.interpolate(0), false);
			setLinearInterpolationDuration(entity, config.interpolationDuration(), false);
		});
	}

	private void setLinearInterpolationDuration(Entity display, int duration, boolean interpolating) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothEntityPathTransition#setLinearInterpolationDuration", Cutscenes.LOGGER)) {
			var writeView = TagValueOutput.createWithContext(logging, display.registryAccess());
			display.saveWithoutId(writeView);
			var data = writeView.buildResult();
			data.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, duration);
			data.putInt(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, duration);
			if (interpolating) {
				data.putInt(Display.TAG_TRANSFORMATION_START_INTERPOLATION, 0);
			}
			var readView = TagValueInput.create(logging, display.registryAccess(), data);
			display.load(readView);
		}
	}

	private void setData(Entity display, SmoothEntityPathConfig.DisplayEntityTarget target, boolean interpolate) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothEntityPathTransition#setData", Cutscenes.LOGGER)) {
			var writeView = TagValueOutput.createWithContext(logging, display.registryAccess());
			display.saveWithoutId(writeView);
			var data = writeView.buildResult();
			data.store(Display.TAG_TRANSFORMATION, Transformation.EXTENDED_CODEC, target.transformation());
			data.putFloat(Display.TAG_SHADOW_RADIUS, target.shadowRadius());
			data.putFloat(Display.TAG_SHADOW_STRENGTH, target.shadowStrength());
			data.putInt("background", target.background());
			data.putByte("text_opacity", target.textOpacity());
			if (interpolate) {
				data.putInt(Display.TAG_TRANSFORMATION_START_INTERPOLATION, 0);
			}
			var readView = TagValueInput.create(logging, display.registryAccess(), data);
			display.load(readView);
		}
		display.absSnapTo(
				target.target().pos().x,
				target.target().pos().y,
				target.target().pos().z,
				target.target().yaw(), target.target().pitch()
		);
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (interpolationSet == null) {
			init(cutscene, interval);
		}
		int currentTimeAdjusted = cutscene.getCurrentTime() + config.interpolationDuration();
		if (currentTimeAdjusted > interval.getEnd()) {
			config.entity().get(cutscene.getRefContext()).forEach(
					e -> setLinearInterpolationDuration(
							e, config.interpolationDuration() - (currentTimeAdjusted - interval.getEnd()), true
					)
			);
			return;
		}
		if (cutscene.getCurrentTime() != interval.getStart()) {
			double delta = ((double) currentTimeAdjusted - interval.getStart()) / interval.getLength();
			var target = interpolationSet.interpolate(delta);
			config.entity().get(cutscene.getRefContext()).forEach(entity -> {
				if ((cutscene.getCurrentTime() - interval.getStart()) % config.teleportInterval() == 0) {
					setData(entity, target, true);
				}
			});
		}
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SMOOTH_ENTITY_PATH;
	}

}
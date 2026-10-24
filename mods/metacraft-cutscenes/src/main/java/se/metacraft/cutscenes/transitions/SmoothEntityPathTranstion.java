package se.metacraft.cutscenes.transitions;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec2;
import se.metacraft.cutscenes.Cutscenes;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.cutscenes.registry.TransitionRegistry;
import se.metacraft.cutscenes.transitions.config.SmoothEntityPathConfig;
import se.metacraft.cutscenes.util.CutsceneContext;
import se.metacraft.core.util.InterpolationSet;
import se.metacraft.cutscenes.util.IntervalMap;
import se.metacraft.lib.util.error_reporters.LoggingErrorReporter;

public class SmoothEntityPathTranstion implements Transition {

	public static final MapCodec<SmoothEntityPathTranstion> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SmoothEntityPathConfig.CODEC.forGetter(t -> t.config)
			).apply(instance, SmoothEntityPathTranstion::new)
	);

	private final SmoothEntityPathConfig config;
	private InterpolationSet<CutsceneContext, SmoothEntityPathConfig.DynamicDisplayTarget> interpolationSet;

	public SmoothEntityPathTranstion(SmoothEntityPathConfig config) {
		this.config = config;
	}

	private void init(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		interpolationSet = config.targets().getTargets(interval);
		var target = config.entity().get(cutscene.getRefContext()).findAny().map(
			SmoothEntityPathConfig.DynamicDisplayTarget::fromEntity
		).orElse(
			SmoothEntityPathConfig.DynamicDisplayTarget.DEFAULT
		);
		interpolationSet = interpolationSet.setStartIfNotPresent(target);
		interpolationSet = interpolationSet.setEndIfNotPresent(target);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		init(cutscene, interval);
		config.entity().get(cutscene.getRefContext()).forEach(entity -> {
			setData(entity, interpolationSet.interpolate(new CutsceneContext(entity, cutscene), 0), false, cutscene);
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

	private void setData(
		Entity display, SmoothEntityPathConfig.DynamicDisplayTarget target, boolean interpolate,
		CutsceneInstance cutscene
	) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothEntityPathTransition#setData", Cutscenes.LOGGER)) {
			var writeView = TagValueOutput.createWithContext(logging, display.registryAccess());
			display.saveWithoutId(writeView);
			var data = writeView.buildResult();
			data.store(Display.TAG_TRANSFORMATION, Transformation.EXTENDED_CODEC, target.displayEntityProperties().transformation());
			data.putFloat(Display.TAG_SHADOW_RADIUS, target.displayEntityProperties().shadowRadius());
			data.putFloat(Display.TAG_SHADOW_STRENGTH, target.displayEntityProperties().shadowStrength());
			data.putInt("background", target.displayEntityProperties().background());
			data.putByte("text_opacity", target.displayEntityProperties().textOpacity());
			if (interpolate) {
				data.putInt(Display.TAG_TRANSFORMATION_START_INTERPOLATION, 0);
			}
			var readView = TagValueInput.create(logging, display.registryAccess(), data);
			display.load(readView);
		}
		var pos = target.target().pos().get(cutscene.createRefContext(display)).orElse(display.position());
		var rot = target.target().rot().get(cutscene.createRefContext(display)).orElseGet(() -> new Vec2(display.getXRot(), display.getYRot()));
		display.absSnapTo(
				pos.x, pos.y, pos.z,
				rot.y, rot.x
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
			config.entity().get(cutscene.getRefContext()).forEach(entity -> {
				var target = interpolationSet.interpolate(new CutsceneContext(entity, cutscene), delta);
				if ((cutscene.getCurrentTime() - interval.getStart()) % config.teleportInterval() == 0) {
					setData(entity, target, true, cutscene);
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
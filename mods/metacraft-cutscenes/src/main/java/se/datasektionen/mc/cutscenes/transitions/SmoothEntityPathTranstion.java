package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.math.AffineTransformation;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.SmoothEntityPathConfig;
import se.datasektionen.mc.cutscenes.util.CutsceneContext;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_lib.util.error_reporters.LoggingErrorReporter;

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
			var writeView = NbtWriteView.create(logging, display.getRegistryManager());
			display.writeData(writeView);
			var data = writeView.getNbt();
			data.putInt(DisplayEntity.TELEPORT_DURATION_KEY, duration);
			data.putInt(DisplayEntity.INTERPOLATION_DURATION_KEY, duration);
			if (interpolating) {
				data.putInt(DisplayEntity.START_INTERPOLATION_KEY, 0);
			}
			var readView = NbtReadView.create(logging, display.getRegistryManager(), data);
			display.readData(readView);
		}
	}

	private void setData(Entity display, SmoothEntityPathConfig.DisplayEntityTarget target, boolean interpolate) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothEntityPathTransition#setData", Cutscenes.LOGGER)) {
			var writeView = NbtWriteView.create(logging, display.getRegistryManager());
			display.writeData(writeView);
			var data = writeView.getNbt();
			data.put(DisplayEntity.TRANSFORMATION_NBT_KEY, AffineTransformation.ANY_CODEC, target.transformation());
			data.putFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY, target.shadowRadius());
			data.putFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY, target.shadowStrength());
			data.putInt("background", target.background());
			data.putByte("text_opacity", target.textOpacity());
			if (interpolate) {
				data.putInt(DisplayEntity.START_INTERPOLATION_KEY, 0);
			}
			var readView = NbtReadView.create(logging, display.getRegistryManager(), data);
			display.readData(readView);
		}
		display.updatePositionAndAngles(
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
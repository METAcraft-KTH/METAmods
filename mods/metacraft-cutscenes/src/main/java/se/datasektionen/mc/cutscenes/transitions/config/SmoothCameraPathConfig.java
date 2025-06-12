package se.datasektionen.mc.cutscenes.transitions.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.Codecs;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.SmoothCameraPathTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.util.InterpolationSetContainer;
import se.datasektionen.mc.cutscenes.util.Target;

public record SmoothCameraPathConfig(
		InterpolationSetContainer<Target> targets, int interpolationDuration, int teleportInterval
) implements TransitionConfig {

	public static final MapCodec<InterpolationSetContainer<Target>> SMOOTH_PATH = InterpolationSetContainer.createCodec(
			Target.MAP_CODEC, Target::fromList, Target.ADJUSTER
	);

	public static final MapCodec<SmoothCameraPathConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SMOOTH_PATH.forGetter(c -> c.targets),
					Codecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(SmoothCameraPathConfig::interpolationDuration),
					Codecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(SmoothCameraPathConfig::teleportInterval)
			).apply(instance, SmoothCameraPathConfig::new)
	);

	@Override
	public Transition create() {
		return new SmoothCameraPathTransition(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.CAMERA_PATH;
	}

}

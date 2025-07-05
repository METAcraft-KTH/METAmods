package nu.metacraft.cutscenes.transitions.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.Codecs;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.SmoothCameraPathTransition;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.util.InterpolationSetContainer;
import nu.metacraft.cutscenes.util.Target;

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

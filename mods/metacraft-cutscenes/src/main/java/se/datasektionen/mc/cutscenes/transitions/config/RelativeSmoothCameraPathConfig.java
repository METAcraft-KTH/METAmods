package se.datasektionen.mc.cutscenes.transitions.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.SmoothCameraPathTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.util.InterpolationSet;
import se.datasektionen.mc.cutscenes.util.Target;

import java.util.stream.DoubleStream;

public record RelativeSmoothCameraPathConfig(InterpolationSet<Target> targets) implements TransitionConfig {

	public static final Codec<InterpolationSet<Target>> INTERPOLATION_SET_CODEC = InterpolationSet.createCodec(Target.MAP_CODEC, s -> Target.fromList((DoubleStream) s));

	public static final MapCodec<RelativeSmoothCameraPathConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					INTERPOLATION_SET_CODEC.fieldOf("targets").forGetter(c -> c.targets)
			).apply(instance, RelativeSmoothCameraPathConfig::new)
	);

	@Override
	public Transition create() {
		return new SmoothCameraPathTransition(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.CAMERA_PATH_RELATIVE;
	}


}

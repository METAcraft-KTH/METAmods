package se.datasektionen.mc.cutscenes.transitions.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.MovingTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.util.Target;

public record MovingTransitionConfig(Target to) implements TransitionConfig {

	public static final MapCodec<MovingTransitionConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Target.CODEC.fieldOf("to").forGetter(c -> c.to)
			).apply(instance, MovingTransitionConfig::new)
	);

	@Override
	public Transition create() {
		return new MovingTransition(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.MOVING_TRANSITION_CONFIG;
	}
}

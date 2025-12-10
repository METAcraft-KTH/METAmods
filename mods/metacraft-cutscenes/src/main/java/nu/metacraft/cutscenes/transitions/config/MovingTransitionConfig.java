package nu.metacraft.cutscenes.transitions.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.MovingTransition;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.util.Target;

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

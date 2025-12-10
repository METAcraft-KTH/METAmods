package nu.metacraft.cutscenes.transitions.config;

import nu.metacraft.cutscenes.transitions.Transition;

public interface TransitionConfig {

	Transition create();

	TransitionConfigType<?> getConfigType();

}

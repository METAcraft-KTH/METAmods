package se.metacraft.cutscenes.transitions.config;

import se.metacraft.cutscenes.transitions.Transition;

public interface TransitionConfig {

	Transition create();

	TransitionConfigType<?> getConfigType();

}

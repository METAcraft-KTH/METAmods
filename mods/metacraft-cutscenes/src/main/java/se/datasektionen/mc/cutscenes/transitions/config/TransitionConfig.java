package se.datasektionen.mc.cutscenes.transitions.config;

import se.datasektionen.mc.cutscenes.transitions.Transition;

public interface TransitionConfig {

	Transition create();

	TransitionConfigType<?> getConfigType();

}

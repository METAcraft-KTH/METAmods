package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;

public record TransitionType<T extends Transition>(MapCodec<T> codec) {
}

package nu.metacraft.cutscenes.transitions.config;

import com.mojang.serialization.MapCodec;

public record TransitionConfigType<T extends TransitionConfig>(MapCodec<T> codec) {
}

package se.datasektionen.mc.cutscenes.rotation_ref;

import com.mojang.serialization.MapCodec;

public record RotationRefType<T extends RotationRef>(MapCodec<T> codec) {
}

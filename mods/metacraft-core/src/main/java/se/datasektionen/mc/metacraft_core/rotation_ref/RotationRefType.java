package se.datasektionen.mc.metacraft_core.rotation_ref;

import com.mojang.serialization.MapCodec;

public record RotationRefType<T extends RotationRef>(MapCodec<T> codec) {
}

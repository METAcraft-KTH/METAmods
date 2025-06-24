package se.datasektionen.mc.metacraft_core.position_ref;

import com.mojang.serialization.MapCodec;

public record PositionRefType<T extends PositionRef>(MapCodec<T> codec) {
}

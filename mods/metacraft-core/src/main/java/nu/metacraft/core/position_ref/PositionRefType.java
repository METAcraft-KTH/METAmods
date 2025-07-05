package nu.metacraft.core.position_ref;

import com.mojang.serialization.MapCodec;

public record PositionRefType<T extends PositionRef>(MapCodec<T> codec) {
}

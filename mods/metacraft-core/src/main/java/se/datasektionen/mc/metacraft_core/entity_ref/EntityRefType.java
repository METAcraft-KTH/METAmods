package se.datasektionen.mc.metacraft_core.entity_ref;

import com.mojang.serialization.MapCodec;

public record EntityRefType<T extends EntityRef>(MapCodec<T> codec) {
}

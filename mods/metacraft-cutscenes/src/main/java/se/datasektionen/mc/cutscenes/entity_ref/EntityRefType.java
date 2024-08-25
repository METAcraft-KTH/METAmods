package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.MapCodec;

public record EntityRefType<T extends EntityRef>(MapCodec<T> codec) {
}

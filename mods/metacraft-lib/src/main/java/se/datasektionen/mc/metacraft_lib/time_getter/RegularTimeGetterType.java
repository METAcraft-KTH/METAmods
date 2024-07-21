package se.datasektionen.mc.metacraft_lib.time_getter;

import com.mojang.serialization.MapCodec;

public record RegularTimeGetterType<T extends RegularTimeGetter>(MapCodec<T> codec) {

}

package se.datasektionen.mc.zones.zone.data;

import com.mojang.serialization.MapCodec;

import java.util.function.Supplier;

public record ZoneDataType<T extends ZoneData>(MapCodec<T> codec, Supplier<T> creator) {}

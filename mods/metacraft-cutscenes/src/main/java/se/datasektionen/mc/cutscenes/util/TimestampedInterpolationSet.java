package se.datasektionen.mc.cutscenes.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.Function;
import net.minecraft.util.math.MathHelper;
import sigbla.app.pds.collection.TreeMap;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class TimestampedInterpolationSet<T extends Interpolatable> {
	private static <T extends Interpolatable> Codec<Map.Entry<Integer, T>> createEntryCodec(MapCodec<T> valueCodec) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("time").forGetter(Map.Entry::getKey),
						valueCodec.forGetter(Map.Entry::getValue)
				).apply(instance, AbstractMap.SimpleEntry::new)
		);
	}

	public static <T extends Interpolatable> Codec<TimestampedInterpolationSet<T>> createCodec(
			MapCodec<T> valueCodec, Function<DoubleStream, T> creator
	) {
		return createEntryCodec(valueCodec).listOf().xmap(
				list -> new TimestampedInterpolationSet<>(
						list.stream().collect(
								Collectors.toMap(
										Map.Entry::getKey,
										Map.Entry::getValue,
										(lhs, rhs) -> lhs,
										java.util.TreeMap::new
								)
						), creator
				),
				set -> set.values.entrySet().stream().toList()
		);
	}

	private final Map<Integer, T> values;
	private final Function<DoubleStream, T> creator;

	public TimestampedInterpolationSet(Map<Integer, T> values, Function<DoubleStream, T> creator) {
		this.values = values;
		this.creator = creator;
	}

	public InterpolationSet<T> createFromRange(int start, int end) {
		return new InterpolationSet<>(
				values.entrySet().stream().map(e -> Pair.of(
						MathHelper.clamp(((double) e.getKey() - start) / (end - start), 0, 1),
						e.getValue()
				)).reduce(
						new TreeMap<>(),
						(map, entry) -> map.put(entry.getFirst(), entry.getSecond()),
						(m, n) -> m
				), creator
		);
	}
}

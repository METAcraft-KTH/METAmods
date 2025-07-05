package nu.metacraft.cutscenes.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import org.pcollections.TreePMap;
import nu.metacraft.core.util.Interpolatable;
import nu.metacraft.core.util.InterpolationSet;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TimestampedInterpolationSet<T extends Interpolatable<CutsceneContext>> {
	private static <T extends Interpolatable<CutsceneContext>> Codec<Map.Entry<Integer, T>> createEntryCodec(MapCodec<T> valueCodec) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.INT.fieldOf("time").forGetter(Map.Entry::getKey),
						valueCodec.forGetter(Map.Entry::getValue)
				).apply(instance, AbstractMap.SimpleEntry::new)
		);
	}

	public static <T extends Interpolatable<CutsceneContext>> Codec<TimestampedInterpolationSet<T>> createCodec(
			MapCodec<T> valueCodec, InterpolationSet.Creator<CutsceneContext, T> creator
	) {
		return createCodec(valueCodec, creator, Int2ObjectMaps.emptyMap());
	}

	public static <T extends Interpolatable<CutsceneContext>> Codec<TimestampedInterpolationSet<T>> createCodec(
			MapCodec<T> valueCodec, InterpolationSet.Creator<CutsceneContext, T> creator, Int2ObjectMap<InterpolationSet.Adjuster> adjusters
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
						), creator, adjusters
				),
				set -> set.values.entrySet().stream().toList()
		);
	}

	private final Map<Integer, T> values;
	private final InterpolationSet.Creator<CutsceneContext, T> creator;
	private final Int2ObjectMap<InterpolationSet.Adjuster> adjusters;

	public TimestampedInterpolationSet(Map<Integer, T> values, InterpolationSet.Creator<CutsceneContext, T> creator, Int2ObjectMap<InterpolationSet.Adjuster> adjusters) {
		this.values = values;
		this.creator = creator;
		this.adjusters = adjusters;
	}

	public InterpolationSet<CutsceneContext, T> createFromRange(int start, int end) {
		return new InterpolationSet<>(
				values.entrySet().stream().filter(
						e -> e.getKey() >= start && e.getKey() <= end
				).collect(
						TreePMap.toTreePMap(
								e -> ((double) e.getKey() - start) / (end - start),
								Map.Entry::getValue
						)
				), creator, adjusters
		);
	}
}

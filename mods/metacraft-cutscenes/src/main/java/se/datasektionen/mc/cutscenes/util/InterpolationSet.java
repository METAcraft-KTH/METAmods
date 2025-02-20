package se.datasektionen.mc.cutscenes.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jetbrains.annotations.Nullable;
import org.pcollections.TreePMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

import java.util.*;
import java.util.function.Function;
import java.util.stream.DoubleStream;

public class InterpolationSet<T extends Interpolatable> {

	private static <T extends Interpolatable> Codec<java.util.Map.Entry<Double, T>> createEntryCodec(MapCodec<T> valueCodec) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.doubleRange(0, 1).fieldOf("index").forGetter(java.util.Map.Entry::getKey),
						valueCodec.forGetter(java.util.Map.Entry::getValue)
				).apply(instance, AbstractMap.SimpleEntry::new)
		);
	}

	public static <T extends Interpolatable> Codec<InterpolationSet<T>> createCodec(
			MapCodec<T> valueCodec, Creator<T> creator
	) {
		return createEntryCodec(valueCodec).listOf().xmap(
				list -> new InterpolationSet<>(
						list.stream().collect(
								TreePMap.toTreePMap(Map.Entry::getKey, Map.Entry::getValue)
						), creator
				),
				set -> set.values.entrySet().stream().toList()
		);
	}

	private static final SplineInterpolator INTERPOLATOR = new SplineInterpolator();

	private final TreePMap<Double, T> values; //Warning, this is a persistent map, not a normal map! That means to update it you must do = just like when updating strings!
	private List<PolynomialSplineFunction> splines;
	private final Creator<T> creator;
	private final boolean dynamic;

	public InterpolationSet(TreePMap<Double, T> values, Creator<T> creator) {
		this.values = values;
		this.dynamic = values.values().stream().anyMatch(Interpolatable::isDynamic);
		this.creator = creator;
	}

	public InterpolationSet<T> setStartIfNotPresent(T start) {
		if (!values.containsKey(0.0)) {
			return new InterpolationSet<>(values.plus(0.0, start), creator);
		}
		return this;
	}

	public InterpolationSet<T> setEndIfNotPresent(T end) {
		if (!values.containsKey(1.0)) {
			return new InterpolationSet<>(values.plus(1.0, end), creator);
		}
		return this;
	}

	public <R extends Interpolatable> InterpolationSet<R> map(Function<T, R> mapper, Creator<R> creator) {
		return new InterpolationSet<>(
				values.entrySet().stream().reduce(
						TreePMap.empty(),
						(map, entry) -> map.plus(entry.getKey(), mapper.apply(entry.getValue())),
						TreePMap::plusAll
				), creator
		);
	}

	private boolean needsUpdate(@Nullable ServerPlayerEntity player, @Nullable CutsceneInstance cutscene) {
		if (dynamic && cutscene != null) {
			for (var t : values.entrySet()) {
				var actualValue = t.getValue().getValues(player, cutscene).toDoubleArray();
				var currentValue = splines.stream().mapToDouble(
						spline -> spline.value(t.getKey())
				).toArray();
				if (!Arrays.equals(actualValue, currentValue)) return true;
			}
		}
		return false;
	}

	private void initSplines(@Nullable ServerPlayerEntity player, @Nullable CutsceneInstance cutscene) {
		if (splines != null && !needsUpdate(player, cutscene)) return;
		DoubleList x = new DoubleArrayList();
		int size = values.values().stream().map(e -> e.getValues(player, cutscene).size()).findAny().orElse(0);
		List<DoubleList> y = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			y.add(new DoubleArrayList());
		}
		values.forEach((key, value) -> {
			x.add((double) key);

			for (int i = 0; i < size; i++) {
				y.get(i).add(value.getValues(player, cutscene).getDouble(i));
			}
		});

		if (x.size() < 2) return;

		if (x.size() == 2) {
			x.add(1, 0.5);
			for (int i = 0; i < size; i++) {
				var list = y.get(i);
				list.add(1, (list.getFirst() + list.getLast()) / 2);
			}
		}

		var xArr = x.toDoubleArray();
		this.splines = y.stream().map(
				list -> INTERPOLATOR.interpolate(xArr, list.toDoubleArray())
		).toList();
	}

	public T interpolate(double delta) {
		return interpolate(null, null, delta);
	}

	public T interpolate(@Nullable ServerPlayerEntity player, @Nullable CutsceneInstance cutscene, double delta) {
		initSplines(player, cutscene);
		if (splines == null) return values.values().stream().findAny().orElse(null);
		return creator.create(splines.stream().mapToDouble(
				spline -> spline.value(delta)
		));
	}

	@FunctionalInterface
	public interface Creator<T extends Interpolatable> {
		T create(DoubleStream stream);
	}
}
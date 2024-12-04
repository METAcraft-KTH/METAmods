package se.datasektionen.mc.cutscenes.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import sigbla.app.pds.collection.Map;
import sigbla.app.pds.collection.TreeMap;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
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
			MapCodec<T> valueCodec, Function<DoubleStream, T> creator
	) {
		return createEntryCodec(valueCodec).listOf().xmap(
				list -> new InterpolationSet<>(
						list.stream().reduce(
								new TreeMap<>(),
								(map, entry) -> map.put(entry.getKey(), entry.getValue()),
								(m, m2) -> m
						), creator
				),
				set -> set.values.asMap().entrySet().stream().toList()
		);
	}

	private static final SplineInterpolator INTERPOLATOR = new SplineInterpolator();

	private final Map<Double, T> values; //Warning, this is a persistent map, not a normal map! That means to update it you must do = just like when updating strings!
	private List<PolynomialSplineFunction> splines;
	private final Function<DoubleStream, T> creator;

	public InterpolationSet(Map<Double, T> values, Function<DoubleStream, T> creator) {
		this.values = values;
		this.creator = creator;
	}

	public InterpolationSet<T> setStartIfNotPresent(T start) {
		if (!values.containsKey(0.0)) {
			return new InterpolationSet<>(values.put(0.0, start), creator);
		}
		return this;
	}

	public InterpolationSet<T> setEndIfNotPresent(T end) {
		if (!values.containsKey(1.0)) {
			return new InterpolationSet<>(values.put(1.0, end), creator);
		}
		return this;
	}

	private void initSplines() {
		if (splines != null) return;
		DoubleList x = new DoubleArrayList();
		int size = values.asMap().values().stream().map(e -> e.getValues().size()).findAny().orElse(0);
		List<DoubleList> y = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			y.add(new DoubleArrayList());
		}
		values.asMap().forEach((key, value) -> {
			x.add((double) key);

			for (int i = 0; i < size; i++) {
				y.get(i).add(value.getValues().getDouble(i));
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
		initSplines();
		if (splines == null) return values.asMap().values().stream().findAny().orElse(null);
		return creator.apply(splines.stream().mapToDouble(
				spline -> spline.value(delta)
		));
	}
}
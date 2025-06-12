package se.datasektionen.mc.metacraft_core.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jetbrains.annotations.Nullable;
import org.pcollections.TreePMap;

import java.util.*;
import java.util.stream.DoubleStream;

public class InterpolationSet<C, T extends Interpolatable<C>> {

	private static <C, T extends Interpolatable<C>> Codec<java.util.Map.Entry<Double, T>> createEntryCodec(MapCodec<T> valueCodec) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.doubleRange(0, 1).fieldOf("index").forGetter(java.util.Map.Entry::getKey),
						valueCodec.forGetter(java.util.Map.Entry::getValue)
				).apply(instance, AbstractMap.SimpleEntry::new)
		);
	}


	public static <C, T extends Interpolatable<C>> Codec<InterpolationSet<C, T>> createCodec(
			MapCodec<T> valueCodec, Creator<C, T> creator
	) {
		return createCodec(valueCodec, creator, Int2ObjectMaps.emptyMap());
	}

	public static <C, T extends Interpolatable<C>> Codec<InterpolationSet<C, T>> createCodec(
			MapCodec<T> valueCodec, Creator<C, T> creator, Int2ObjectMap<Adjuster> adjusters
	) {
		return createEntryCodec(valueCodec).listOf().xmap(
				list -> new InterpolationSet<>(
						list.stream().collect(
								TreePMap.toTreePMap(Map.Entry::getKey, Map.Entry::getValue)
						), creator, adjusters
				),
				set -> set.values.entrySet().stream().toList()
		);
	}

	private static final SplineInterpolator INTERPOLATOR = new SplineInterpolator();

	private final TreePMap<Double, T> values; //Warning, this is a persistent map, not a normal map! That means to update it you must do = just like when updating strings!
	private List<PolynomialSplineFunction> splines;
	private final Creator<C, T> creator;
	private final Int2ObjectMap<Adjuster> adjusters;
	private final boolean dynamic;

	public InterpolationSet(TreePMap<Double, T> values, Creator<C, T> creator) {
		this(values, creator, Int2ObjectMaps.emptyMap());
	}

	public InterpolationSet(TreePMap<Double, T> values, Creator<C, T> creator, Int2ObjectMap<Adjuster> adjusters) {
		this.values = values;
		this.dynamic = values.values().stream().anyMatch(Interpolatable::isDynamic);
		this.creator = creator;
		this.adjusters = adjusters;
	}

	public InterpolationSet<C, T> setStartIfNotPresent(T start) {
		if (!values.containsKey(0.0)) {
			return new InterpolationSet<>(values.plus(0.0, start), creator, adjusters);
		}
		return this;
	}

	public InterpolationSet<C, T> setEndIfNotPresent(T end) {
		if (!values.containsKey(1.0)) {
			return new InterpolationSet<>(values.plus(1.0, end), creator, adjusters);
		}
		return this;
	}

	private boolean needsUpdate(@Nullable C context) {
		if (dynamic && context != null) {
			for (var t : values.entrySet()) {
				var actualValue = t.getValue().getValues(context).toDoubleArray();
				var currentValue = splines.stream().mapToDouble(
						spline -> spline.value(t.getKey())
				).toArray();
				if (!Arrays.equals(actualValue, currentValue)) return true;
			}
		}
		return false;
	}

	private void initSplines(@Nullable C context) {
		if (splines != null && !needsUpdate(context)) return;
		DoubleList x = new DoubleArrayList();
		int size = values.values().stream().map(e -> e.getValues(context).size()).findAny().orElse(0);
		List<DoubleList> y = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			y.add(new DoubleArrayList());
		}
		values.forEach((key, value) -> {
			x.add((double) key);

			for (int i = 0; i < size; i++) {
				y.get(i).add(value.getValues(context).getDouble(i));
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

		for (int i = 0; i < y.size(); i++) {
			if (adjusters.containsKey(i)) {
				y.set(i, adjusters.get(i).adjustValues(y.get(i)));
			}
		}

		var xArr = x.toDoubleArray();
		this.splines = y.stream().map(
				list -> INTERPOLATOR.interpolate(xArr, list.toDoubleArray())
		).toList();
	}

	public T interpolate(double delta) {
		return interpolate(null, delta);
	}

	public T interpolate(@Nullable C context, double delta) {
		initSplines(context);
		if (splines == null) return values.values().stream().findAny().orElse(null);
		return creator.create(splines.stream().mapToDouble(
				spline -> spline.value(delta)
		));
	}

	@FunctionalInterface
	public interface Creator<C, T extends Interpolatable<C>> {
		T create(DoubleStream stream);
	}

	@FunctionalInterface
	public interface Adjuster {
		DoubleList adjustValues(DoubleList elements);
	}

	public static DoubleList fixYawRotations(DoubleList elements) {
		if (elements.size() < 2) return elements;
		for (double e : elements) {
			if (Math.abs(e) > 180) {
				return elements;
			}
		}
		elements = new DoubleArrayList(elements);
		double prev = elements.getFirst();

		double offset = 0;

		for (int i = 1; i < elements.size(); i++) {
			double current = elements.getDouble(i);

			if (current - prev > 180) { //-180 -> 180
				offset -= 360;
			} else if (current - prev < -180) { //180 -> -180
				offset += 360;
			}

			prev = current;
			if (offset != 0) {
				current += offset;
				elements.set(i, current);
			}
		}

		return elements;
	}
}
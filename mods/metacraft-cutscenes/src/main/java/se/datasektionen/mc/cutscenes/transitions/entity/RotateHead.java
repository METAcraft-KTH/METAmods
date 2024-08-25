package se.datasektionen.mc.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Util;
import net.minecraft.util.math.floatprovider.FloatProvider;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.NotNull;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.MovingTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;

import java.util.*;
import java.util.stream.Collectors;

public class RotateHead implements Transition {

	public static final MapCodec<RotateHead> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					RotateHeadConfig.CODEC.forGetter(a -> a.config),
					InterpolationSet.CODEC.optionalFieldOf("yaw").forGetter(a -> Optional.ofNullable(a.yaw)),
					InterpolationSet.CODEC.optionalFieldOf("pitch").forGetter(a -> Optional.ofNullable(a.pitch))
			).apply(instance, RotateHead::new)
	);

	private final RotateHeadConfig config;
	private InterpolationSet yaw = null;
	private InterpolationSet pitch = null;

	public RotateHead(RotateHeadConfig config) {
		this.config = config;
	}

	public RotateHead(RotateHeadConfig config, Optional<InterpolationSet> yaw, Optional<InterpolationSet> pitch) {
		this(config);
		this.yaw = yaw.orElse(null);
		this.pitch = pitch.orElse(null);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		MutableFloat prevYaw = new MutableFloat(0);
		MutableFloat currentYaw = new MutableFloat(0);
		MutableFloat prevPitch = new MutableFloat(0);
		MutableFloat currentPitch = new MutableFloat(0);

		config.entity.get(null, cutscene).findAny().ifPresent(entity -> {
			prevYaw.add(entity.prevYaw);
			currentYaw.add(entity.getYaw());
			prevPitch.add(entity.prevPitch);
			currentPitch.add(entity.getPitch());
		});

		yaw = InterpolationSet.from(prevYaw.floatValue(), currentYaw.floatValue(), config.offsets.stream().map(
				offset -> new InterpolationSet.Entry(offset.delta, currentYaw.floatValue() + offset.yawOffset.get(cutscene.getRandom()))
		).collect(Collectors.toCollection(TreeSet::new)));
		pitch = InterpolationSet.from(prevPitch.floatValue(), currentPitch.floatValue(), config.offsets.stream().map(
				offset -> new InterpolationSet.Entry(offset.delta, currentPitch.floatValue() + offset.pitchOffset.get(cutscene.getRandom()))
		).collect(Collectors.toCollection(TreeSet::new)));
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity.get(null, cutscene).forEach(entity -> {
			float delta = interval.getDelta(cutscene.getCurrentTime());
			entity.prevYaw = entity.getYaw();
			entity.prevPitch = entity.getPitch();
			entity.setYaw(yaw.interpolate(delta));
			entity.setHeadYaw(entity.getYaw());
			entity.setPitch(pitch.interpolate(delta));
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ROTATE_HEAD;
	}

	public record InterpolationSet(RangeMap<Float, Float> values) {

		public static final Codec<InterpolationSet> CODEC = Codec.FLOAT.listOf().comapFlatMap(
				values -> Util.decodeFixedLengthList(values, 3), values -> values
		).listOf().xmap(
				list -> new InterpolationSet(
						list.stream().collect(
								ImmutableRangeMap.toImmutableRangeMap(
										entry -> Range.closedOpen(entry.get(0), entry.get(1)),
										entry -> entry.get(2)
								)
						)
				),
				set -> set.values.asMapOfRanges().entrySet().stream().map(
						entry -> List.of(entry.getKey().lowerEndpoint(), entry.getKey().upperEndpoint(), entry.getValue())
				).toList()
		);

		public float getDelta(float delta, Range<Float> range) {
			return (delta - range.lowerEndpoint()) / (range.upperEndpoint() - range.lowerEndpoint());
		}

		public float interpolate(float delta) {
			var entry = values.getEntry(delta);
			if (entry == null) return 0;
			var prev = values.getEntry(Float.intBitsToFloat(Float.floatToIntBits(entry.getKey().lowerEndpoint())-1));
			var next = values.getEntry(Float.intBitsToFloat(Float.floatToIntBits(entry.getKey().upperEndpoint())+1));
			var afterNext = next != null ? values.getEntry(Float.intBitsToFloat(Float.floatToIntBits(next.getKey().upperEndpoint())+1)) : null;
			float current = entry.getValue();
			float prevFrom = prev != null ? prev.getValue() : current;
			float to = next != null ? next.getValue() : current;
			float after = afterNext != null ? afterNext.getValue() : to;
			return (float) MovingTransition.interpolate(
					getDelta(delta, entry.getKey()),
					new double[]{prevFrom, current, to, after}
			);
		}

		public static InterpolationSet from(float prevStartingValue, float startingValue, SortedSet<Entry> offsets) {
			var builder = ImmutableRangeMap.<Float, Float>builder();
			if (prevStartingValue != startingValue) {
				builder.put(Range.closedOpen(-1.0f, 0.0f), prevStartingValue);
			}
			float prevStart = 0;
			float value = startingValue;
			for (var offset : offsets) {
				builder.put(Range.closedOpen(prevStart, offset.delta), value);
				prevStart = offset.delta;
				value = offset.offset;
			}
			if (prevStart >= 1) {
				builder.put(Range.closedOpen(prevStart, prevStart + 0.5f), value);
			} else {
				builder.put(Range.closedOpen(prevStart, 1.0f), value);
			}
			return new InterpolationSet(builder.build());
		}

		public record Entry(float delta, float offset) implements Comparable<Entry> {

			@Override
			public int compareTo(@NotNull RotateHead.InterpolationSet.Entry entry) {
				return Float.compare(delta, entry.delta);
			}

			@Override
			public boolean equals(Object other) {
				if (this == other) return true;
				if (other == null) return false;
				if (other.getClass() != this.getClass()) return false;
				return ((Entry) other).delta == delta;
			}
		}
	}

	public record RotateHeadConfig(EntityRef entity, List<OffsetTarget> offsets) implements TransitionConfig {

		public static final MapCodec<RotateHeadConfig> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						EntityRefRegistry.CODEC.fieldOf("entity").forGetter(RotateHeadConfig::entity),
						OffsetTarget.CODEC.listOf().fieldOf("offsets").forGetter(RotateHeadConfig::offsets)
				).apply(instance, RotateHeadConfig::new)
		);

		@Override
		public Transition create() {
			return new RotateHead(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.ROTATE_HEAD;
		}

		public record OffsetTarget(FloatProvider yawOffset, FloatProvider pitchOffset, float delta) {
			public static final Codec<OffsetTarget> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							FloatProvider.createValidatedCodec(-360, 360).fieldOf("yaw_offset").forGetter(OffsetTarget::yawOffset),
							FloatProvider.createValidatedCodec(-360, 360).fieldOf("pitch_offset").forGetter(OffsetTarget::pitchOffset),
							Codec.floatRange(0, 1).fieldOf("delta").forGetter(OffsetTarget::delta)
					).apply(instance, OffsetTarget::new)
			);
		}
	}

}

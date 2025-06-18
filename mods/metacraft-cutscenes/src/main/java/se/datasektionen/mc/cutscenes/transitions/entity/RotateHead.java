package se.datasektionen.mc.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.util.CutsceneContext;
import se.datasektionen.mc.metacraft_core.util.Interpolatable;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;
import se.datasektionen.mc.cutscenes.util.InterpolationSetContainer;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;

import java.util.*;
import java.util.stream.DoubleStream;

public class RotateHead implements Transition {

	public static final MapCodec<RotateHead> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					RotateHeadConfig.CODEC.forGetter(a -> a.config),
					Codec.unboundedMap(Uuids.STRING_CODEC, FixedTarget.CODEC.codec()).fieldOf("entity_facings").forGetter(
							t -> t.entityFacings
					)
			).apply(instance, RotateHead::new)
	);

	private final RotateHeadConfig config;
	private InterpolationSet<CutsceneContext, RotateHeadConfig.OffsetTarget> offsets;
	private final Map<UUID, FixedTarget> entityFacings;

	public RotateHead(RotateHeadConfig config) {
		this(config, new HashMap<>());
	}

	public RotateHead(
			RotateHeadConfig config, Map<UUID, FixedTarget> entityFacings
	) {
		this.config = config;
		this.entityFacings = entityFacings instanceof ImmutableMap<UUID, FixedTarget> ? new HashMap<>(entityFacings) : entityFacings;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		offsets = config.targets.getTargets(interval);
		offsets = offsets.setStartIfNotPresent(new RotateHeadConfig.OffsetTarget(0, 0));
		offsets = offsets.setEndIfNotPresent(new RotateHeadConfig.OffsetTarget(0, 0));
		config.entity.get(cutscene.getRefContext()).findAny().ifPresent(entity -> {
			entityFacings.put(entity.getUuid(), new FixedTarget(entity.getYaw(), entity.getPitch()));
		});
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		if (offsets == null) {
			offsets = config.targets.getTargets(interval);
			offsets = offsets.setStartIfNotPresent(new RotateHeadConfig.OffsetTarget(0, 0));
			offsets = offsets.setEndIfNotPresent(new RotateHeadConfig.OffsetTarget(0, 0));
		}
		config.entity.get(cutscene.getRefContext()).forEach(entity -> {
			float delta = interval.getDelta(cutscene.getCurrentTime());
			entity.prevYaw = entity.getYaw();
			entity.prevPitch = entity.getPitch();
			var offset = offsets.interpolate(delta);
			var origin = entityFacings.computeIfAbsent(
					entity.getUuid(), k -> new FixedTarget(entity.getYaw(), entity.getPitch())
			);
			float newYaw = origin.yaw() + offset.yawOffset();
			if (!config.onlyHead) {
				entity.setYaw(newYaw);
			}
			entity.setHeadYaw(newYaw);
			entity.setPitch(origin.pitch() + offset.pitchOffset());
		});
	}

	public record FixedTarget(float yaw, float pitch) implements Interpolatable<CutsceneContext> {

		public static final MapCodec<FixedTarget> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Codec.FLOAT.fieldOf("yaw").forGetter(FixedTarget::yaw),
						Codec.FLOAT.fieldOf("pitch").forGetter(FixedTarget::pitch)
				).apply(instance, FixedTarget::new)
		);

		@Override
		public DoubleList getValues(@Nullable CutsceneContext ctx) {
			return DoubleList.of(yaw, pitch);
		}

		public static FixedTarget fromList(DoubleStream values) {
			var v = values.limit(2).toArray();
			return new FixedTarget((float) v[0], (float) v[1]);
		}
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ROTATE_HEAD;
	}

	public record RotateHeadConfig(EntityRef entity, InterpolationSetContainer<OffsetTarget> targets, boolean onlyHead) implements TransitionConfig {

		public static final MapCodec<RotateHeadConfig> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						EntityRefRegistry.CODEC.fieldOf("entity").forGetter(RotateHeadConfig::entity),
						InterpolationSetContainer.createCodec(OffsetTarget.CODEC, OffsetTarget::fromList).forGetter(t -> t.targets),
						Codec.BOOL.optionalFieldOf("only_head", false).forGetter(RotateHeadConfig::onlyHead)
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

		public record OffsetTarget(float yawOffset, float pitchOffset) implements Interpolatable<CutsceneContext> {

			public static final MapCodec<OffsetTarget> CODEC = RecordCodecBuilder.mapCodec(
					instance -> instance.group(
							Codec.FLOAT.fieldOf("yaw_offset").forGetter(OffsetTarget::yawOffset),
							Codec.FLOAT.fieldOf("pitch_offset").forGetter(OffsetTarget::pitchOffset)
					).apply(instance, OffsetTarget::new)
			);

			@Override
			public DoubleList getValues(@Nullable CutsceneContext ctx) {
				return DoubleList.of(yawOffset, pitchOffset);
			}

			public static OffsetTarget fromList(DoubleStream values) {
				var v = values.limit(2).toArray();
				return new OffsetTarget((float) v[0], (float) v[1]);
			}
		}
	}

}

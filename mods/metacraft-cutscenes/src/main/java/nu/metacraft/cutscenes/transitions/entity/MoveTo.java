package nu.metacraft.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.mutable.MutableInt;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.core.mixin.AccessorEntityNavigation;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class MoveTo implements Transition {

	public static final MapCodec<MoveTo> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					Codec.unboundedMap(
							UUIDUtil.STRING_CODEC,
							Codec.INT.xmap(MutableInt::new, MutableInt::getValue)
					).fieldOf("current_targets").forGetter(t -> t.currentTargets),
					Codec.INT.optionalFieldOf("current_target", 0).forGetter(t -> t.oldTarget)
			).apply(instance, MoveTo::new)
	);

	private final Config config;
	private final Map<UUID, MutableInt> currentTargets;
	private final int oldTarget;

	public MoveTo(Config config, Map<UUID, MutableInt> currentTargets, int oldTarget) {
		this.config = config;
		this.currentTargets = new HashMap<>(currentTargets);
		this.oldTarget = oldTarget;
	}

	public MoveTo(Config config) {
		this(config, Map.of(), 0);
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	private Path findPath(Mob mob, Config.PathTarget target, Vec3 pos) {
		return ((AccessorEntityNavigation) mob.getNavigation()).callCreatePath(
				ImmutableSet.of(BlockPos.containing(pos)), target.searchRange, target.useHeadPos, target.completionDistance
		);
	}

	private void tryNewPath(Mob mob, Config.PathTarget target, Vec3 pos, double speed) {
		var path = findPath(mob, target, pos);
		if (path != null) {
			mob.getNavigation().moveTo(path, speed);
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity.get(cutscene.getRefContext()).forEach(entity -> {
			if (!currentTargets.containsKey(entity.getUUID())) {
				currentTargets.put(entity.getUUID(), new MutableInt(oldTarget));
			}
			MutableInt currentTarget = currentTargets.get(entity.getUUID());
			if (config.path.size() > currentTarget.getValue()) {
				var target = config.path.get(currentTarget.getValue());
				target.target.get(cutscene.createRefContext(entity)).ifPresent(pos -> {
					double speed = target.speedToTarget.orElse(config.speed);
					if (entity instanceof Mob mob) {
						if (!Objects.equals(BlockPos.containing(pos), mob.getNavigation().getTargetPos())) {
							tryNewPath(mob, target, pos, speed);
						} else {
							if (mob.getNavigation().isDone()) {
								tryNewPath(mob, target, pos, speed);
							}
						}
						if (
								mob.getNavigation().getPath() != null &&
								mob.getNavigation().getPath().isDone() &&
								mob.getNavigation().getPath().canReach()
						) {
							proceedToNextTarget(entity);
							mob.getNavigation().stop();
						}
					} else {
						entity.setDeltaMovement(pos.subtract(entity.position()).normalize().scale(speed));
						if (entity.position().distanceTo(pos) <= target.completionDistance) {
							proceedToNextTarget(entity);
						}
					}
				});
			}
		});
	}

	private void proceedToNextTarget(Entity entity) {
		var currentTarget = currentTargets.get(entity.getUUID());
		currentTarget.increment();
		if (currentTarget.getValue() >= config.path.size()) {
			currentTarget.setValue(config.path.size()-1);
		}
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ENTITY_MOVE_TO;
	}

	public record Config(EntityRef entity, List<PathTarget> path, double speed) implements TransitionConfig {

		public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
						PathTarget.CODEC.listOf().fieldOf("path").forGetter(t -> t.path),
						Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(t -> t.speed)
				).apply(instance, Config::new)
		);

		public record PathTarget(PositionRef target, int searchRange, boolean useHeadPos, int completionDistance, Optional<Double> speedToTarget) {

			public PathTarget(PositionRef target) {
				this(target, 16, true, 1, Optional.empty());
			}

			public static final Codec<PathTarget> CODEC = RecordCodecBuilder.create(
					instance -> instance.group(
							PositionRefRegistry.CODEC.fieldOf("target").forGetter(t -> t.target),
							ExtraCodecs.POSITIVE_INT.optionalFieldOf("search_range", 16).forGetter(t -> t.searchRange),
							Codec.BOOL.optionalFieldOf("use_head_pos", true).forGetter(t -> t.useHeadPos),
							ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("completion_distance", 1).forGetter(t -> t.completionDistance),
							Codec.DOUBLE.optionalFieldOf("speed_to_target").forGetter(t -> t.speedToTarget)
					).apply(instance, PathTarget::new)
			);
		}

		@Override
		public Transition create() {
			return new MoveTo(this);
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.ENTITY_MOVE_TO;
		}
	}
}

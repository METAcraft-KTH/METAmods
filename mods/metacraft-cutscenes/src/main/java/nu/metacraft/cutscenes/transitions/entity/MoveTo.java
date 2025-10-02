package nu.metacraft.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Uuids;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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

public class MoveTo implements Transition {

	public static final MapCodec<MoveTo> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					Codec.unboundedMap(
							Uuids.STRING_CODEC,
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

	private Path findPath(MobEntity mob, Config.PathTarget target, Vec3d pos) {
		return ((AccessorEntityNavigation) mob.getNavigation()).callFindPathTo(
				ImmutableSet.of(BlockPos.ofFloored(pos)), target.searchRange, target.useHeadPos, target.completionDistance
		);
	}

	private void tryNewPath(MobEntity mob, Config.PathTarget target, Vec3d pos, double speed) {
		var path = findPath(mob, target, pos);
		if (path != null) {
			mob.getNavigation().startMovingAlong(path, speed);
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		config.entity.get(cutscene.getRefContext()).forEach(entity -> {
			if (!currentTargets.containsKey(entity.getUuid())) {
				currentTargets.put(entity.getUuid(), new MutableInt(oldTarget));
			}
			MutableInt currentTarget = currentTargets.get(entity.getUuid());
			if (config.path.size() > currentTarget.getValue()) {
				var target = config.path.get(currentTarget.getValue());
				target.target.get(cutscene.createRefContext(entity)).ifPresent(pos -> {
					double speed = target.speedToTarget.orElse(config.speed);
					if (entity instanceof MobEntity mob) {
						if (!Objects.equals(BlockPos.ofFloored(pos), mob.getNavigation().getTargetPos())) {
							tryNewPath(mob, target, pos, speed);
						} else {
							if (mob.getNavigation().isIdle()) {
								tryNewPath(mob, target, pos, speed);
							}
						}
						if (
								mob.getNavigation().getCurrentPath() != null &&
								mob.getNavigation().getCurrentPath().isFinished() &&
								mob.getNavigation().getCurrentPath().reachesTarget()
						) {
							proceedToNextTarget(entity);
							mob.getNavigation().stop();
						}
					} else {
						entity.setVelocity(pos.subtract(entity.getEntityPos()).normalize().multiply(speed));
						if (entity.getEntityPos().distanceTo(pos) <= target.completionDistance) {
							proceedToNextTarget(entity);
						}
					}
				});
			}
		});
	}

	private void proceedToNextTarget(Entity entity) {
		var currentTarget = currentTargets.get(entity.getUuid());
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
							Codecs.POSITIVE_INT.optionalFieldOf("search_range", 16).forGetter(t -> t.searchRange),
							Codec.BOOL.optionalFieldOf("use_head_pos", true).forGetter(t -> t.useHeadPos),
							Codecs.NON_NEGATIVE_INT.optionalFieldOf("completion_distance", 1).forGetter(t -> t.completionDistance),
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

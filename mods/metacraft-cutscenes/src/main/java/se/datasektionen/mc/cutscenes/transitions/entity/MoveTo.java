package se.datasektionen.mc.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.position_ref.PositionRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.metacraft_core.mixin.AccessorEntityNavigation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MoveTo implements Transition {

	public static final MapCodec<MoveTo> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Config.CODEC.forGetter(t -> t.config),
					Codec.INT.fieldOf("current_target").forGetter(t -> t.currentTarget)
			).apply(instance, MoveTo::new)
	);

	private final Config config;
	private int currentTarget;

	public MoveTo(Config config, int currentTarget) {
		this.config = config;
		this.currentTarget = currentTarget;
	}

	public MoveTo(Config config) {
		this(config, 0);
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
		config.entity.get(null, cutscene).forEach(entity -> {
			if (config.path.size() > currentTarget) {
				var target = config.path.get(currentTarget);
				target.target.get(null, cutscene).ifPresent(pos -> {
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
							proceedToNextTarget();
							mob.getNavigation().stop();
						}
					} else {
						entity.setVelocity(pos.subtract(entity.getPos()).normalize().multiply(speed));
						if (entity.getPos().distanceTo(pos) <= target.completionDistance) {
							proceedToNextTarget();
						}
					}
				});
			}
		});
	}

	private void proceedToNextTarget() {
		currentTarget++;
		if (currentTarget >= config.path.size()) {
			currentTarget = config.path.size()-1;
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

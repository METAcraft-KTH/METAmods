package se.datasektionen.mc.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.position_ref.PositionRef;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.metacraft_core.registry.PositionRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_core.mixin.AccessorEntityNavigation;

import java.util.Optional;

public class PathFindTo implements Transition, TransitionConfig {

	public static final MapCodec<PathFindTo> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					PositionRefRegistry.CODEC.fieldOf("target").forGetter(t -> t.target),
					Codecs.POSITIVE_INT.optionalFieldOf("search_range", 16).forGetter(t -> t.searchRange),
					Codecs.POSITIVE_INT.optionalFieldOf("fuzzy_vertical_range").forGetter(t -> t.fuzzyVerticalRange),
					Codec.BOOL.optionalFieldOf("use_head_pos", true).forGetter(t -> t.useHeadPos),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("completion_distance", 1).forGetter(t -> t.completionDistance),
					Codecs.NON_NEGATIVE_INT.optionalFieldOf("fuzzy_completion_distance", 1).forGetter(t -> t.fuzzyCompletionDistance),
					Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(t -> t.speed)
			).apply(instance, PathFindTo::new)
	);

	private final EntityRef entity;
	private final PositionRef target;
	private final int searchRange;
	private final Optional<Integer> fuzzyVerticalRange;
	private final boolean useHeadPos;
	private final int completionDistance;
	private final int fuzzyCompletionDistance;
	private final double speed;

	public PathFindTo(
			EntityRef entity, PositionRef target, int searchRange,
			Optional<Integer> fuzzyVerticalRange, boolean useHeadPos,
			int completionDistance, int fuzzyCompletionDistance, double speed
	) {
		this.entity = entity;
		this.target = target;
		this.searchRange = searchRange;
		this.fuzzyVerticalRange = fuzzyVerticalRange;
		this.useHeadPos = useHeadPos;
		this.completionDistance = completionDistance;
		this.fuzzyCompletionDistance = fuzzyCompletionDistance;
		this.speed = speed;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	private Path findPath(MobEntity mob, Vec3d pos, boolean isFuzzy) {
		return ((AccessorEntityNavigation) mob.getNavigation()).callFindPathTo(
				ImmutableSet.of(BlockPos.ofFloored(pos)), searchRange, useHeadPos, isFuzzy ? fuzzyCompletionDistance : completionDistance
		);
	}

	private void tryNewPath(MobEntity mob, Vec3d pos, double speed, boolean isFuzzy) {
		var path = findPath(mob, pos, isFuzzy);
		if (path != null) {
			mob.getNavigation().startMovingAlong(path, speed);
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).forEach(entity -> {
			target.get(cutscene.createRefContext(entity)).ifPresent(pos -> {
				if (entity instanceof PathAwareEntity mob) {
					if (mob.getNavigation().isIdle() && mob.getPos().distanceTo(pos) > completionDistance) {
						var targetPos = pos;
						boolean isFuzzy = false;
						if (pos.distanceTo(mob.getPos()) >= searchRange) {
							targetPos = FuzzyTargeting.findTo(mob, searchRange, fuzzyVerticalRange.orElse(searchRange/2), pos);
							if (targetPos == null) return;
							isFuzzy = true;
						}
						tryNewPath(mob, targetPos, speed, isFuzzy);
					}
					if (
							mob.getNavigation().getCurrentPath() != null &&
							mob.getNavigation().getCurrentPath().isFinished() &&
							mob.getNavigation().getCurrentPath().reachesTarget()
					) {
						mob.getNavigation().stop();
					}
				} else {
					entity.setVelocity(pos.subtract(entity.getPos()).normalize().multiply(speed));
				}
			});
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ENTITY_FUZZY_FIND_TO;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.ENTITY_FUZZY_FIND_TO;
	}
}

package nu.metacraft.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.core.mixin.PathNavigationAccessor;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class PathFindTo implements Transition, TransitionConfig {

	public static final MapCodec<PathFindTo> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					PositionRefRegistry.CODEC.fieldOf("target").forGetter(t -> t.target),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("search_range", 16).forGetter(t -> t.searchRange),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("fuzzy_vertical_range").forGetter(t -> t.fuzzyVerticalRange),
					Codec.BOOL.optionalFieldOf("use_head_pos", true).forGetter(t -> t.useHeadPos),
					ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("completion_distance", 1).forGetter(t -> t.completionDistance),
					ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("fuzzy_completion_distance", 1).forGetter(t -> t.fuzzyCompletionDistance),
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

	private Path findPath(Mob mob, Vec3 pos, boolean isFuzzy) {
		return ((PathNavigationAccessor) mob.getNavigation()).callCreatePath(
				ImmutableSet.of(BlockPos.containing(pos)), searchRange, useHeadPos, isFuzzy ? fuzzyCompletionDistance : completionDistance
		);
	}

	private void tryNewPath(Mob mob, Vec3 pos, double speed, boolean isFuzzy) {
		var path = findPath(mob, pos, isFuzzy);
		if (path != null) {
			mob.getNavigation().moveTo(path, speed);
		}
	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).forEach(entity -> {
			target.get(cutscene.createRefContext(entity)).ifPresent(pos -> {
				if (entity instanceof PathfinderMob mob) {
					if (mob.getNavigation().isDone() && mob.position().distanceTo(pos) > completionDistance) {
						var targetPos = pos;
						boolean isFuzzy = false;
						if (pos.distanceTo(mob.position()) >= searchRange) {
							targetPos = LandRandomPos.getPosTowards(mob, searchRange, fuzzyVerticalRange.orElse(searchRange/2), pos);
							if (targetPos == null) return;
							isFuzzy = true;
						}
						tryNewPath(mob, targetPos, speed, isFuzzy);
					}
					if (
							mob.getNavigation().getPath() != null &&
							mob.getNavigation().getPath().isDone() &&
							mob.getNavigation().getPath().canReach()
					) {
						mob.getNavigation().stop();
					}
				} else {
					entity.setDeltaMovement(pos.subtract(entity.position()).normalize().scale(speed));
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

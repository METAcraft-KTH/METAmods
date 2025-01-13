package se.datasektionen.mc.cutscenes.transitions.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
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

import java.util.Objects;

public class MoveTo implements Transition, TransitionConfig {

	public static final MapCodec<MoveTo> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					PositionRefRegistry.CODEC.fieldOf("target").forGetter(t -> t.target),
					Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(t -> t.speed)
			).apply(instance, MoveTo::new)
	);

	private final EntityRef entity;
	private final PositionRef target;
	private final double speed;

	public MoveTo(EntityRef entity, PositionRef target, double speed) {
		this.entity = entity;
		this.target = target;
		this.speed = speed;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		this.target.get(null, cutscene).ifPresent(target -> {
			this.entity.get(null, cutscene).forEach(entity -> {
				if (entity instanceof MobEntity mob) {
					if (!Objects.equals(BlockPos.ofFloored(target), mob.getNavigation().getTargetPos())) {
						var path = ((AccessorEntityNavigation) mob.getNavigation()).callFindPathTo(
								ImmutableSet.of(BlockPos.ofFloored(target)), 16, true, 1
						);
						mob.getNavigation().startMovingAlong(path, speed);
					} else {
						if (
								mob.getNavigation().getCurrentPath() == null ||
								!mob.getNavigation().getCurrentPath().reachesTarget() ||
								(mob.getNavigation().isIdle() && !mob.getBlockPos().equals(BlockPos.ofFloored(target)))
						) {
							mob.getNavigation().recalculatePath();
						}
					}
				} else {
					entity.setVelocity(target.subtract(entity.getPos()).normalize().multiply(speed));
				}
			});
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ENTITY_MOVE_TO;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.ENTITY_MOVE_TO;
	}
}

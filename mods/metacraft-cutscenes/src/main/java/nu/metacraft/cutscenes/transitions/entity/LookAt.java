package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

import java.util.Optional;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Mob;

public class LookAt implements Transition, TransitionConfig {

	public static final MapCodec<LookAt> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					PositionRefRegistry.CODEC.fieldOf("target").forGetter(t -> t.target),
					ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("max_yaw_change").forGetter(t -> t.maxYawChange),
					ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("max_pitch_change").forGetter(t -> t.maxPitchChange),
					Codec.BOOL.optionalFieldOf("update_body_yaw", true).forGetter(t -> t.updateBodyYaw)
			).apply(instance, LookAt::new)
	);

	private final EntityRef entity;
	private final PositionRef target;
	private final Optional<Float> maxYawChange;
	private final Optional<Float> maxPitchChange;
	private final boolean updateBodyYaw;

	public LookAt(EntityRef entity, PositionRef target, Optional<Float> maxYawChange, Optional<Float> maxPitchChange, boolean updateBodyYaw) {
		this.entity = entity;
		this.target = target;
		this.maxYawChange = maxYawChange;
		this.maxPitchChange = maxPitchChange;
		this.updateBodyYaw = updateBodyYaw;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).forEach(entity -> {
			target.get(cutscene.createRefContext(entity)).ifPresent(target -> {
				if (entity instanceof Mob mob) {
					mob.getLookControl().setLookAt(
							target.x(), target.y(), target.z(),
							this.maxYawChange.orElse((float) mob.getHeadRotSpeed()),
							maxPitchChange.orElse((float) mob.getMaxHeadXRot())
					);
					if (updateBodyYaw) {
						mob.setYRot(mob.getYHeadRot());
					}
				} else {
					entity.lookAt(EntityAnchorArgument.Anchor.EYES, target);
				}
			});
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.LOOK_AT;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.LOOK_AT;
	}
}

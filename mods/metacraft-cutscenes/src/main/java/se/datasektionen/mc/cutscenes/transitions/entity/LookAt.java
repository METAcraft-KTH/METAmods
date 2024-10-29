package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.dynamic.Codecs;
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

import java.util.Optional;

public class LookAt implements Transition, TransitionConfig {

	public static final MapCodec<LookAt> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					PositionRefRegistry.CODEC.fieldOf("target").forGetter(t -> t.target),
					Codecs.POSITIVE_FLOAT.optionalFieldOf("max_yaw_change").forGetter(t -> t.maxYawChange),
					Codecs.POSITIVE_FLOAT.optionalFieldOf("max_pitch_change").forGetter(t -> t.maxPitchChange),
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
		entity.get(null, cutscene).forEach(entity -> {
			target.get(null, cutscene).ifPresent(target -> {
				if (entity instanceof MobEntity mob) {
					mob.getLookControl().lookAt(
							target.getX(), target.getY(), target.getZ(),
							this.maxYawChange.orElse((float) mob.getMaxLookYawChange()),
							maxPitchChange.orElse((float) mob.getMaxLookPitchChange())
					);
					if (updateBodyYaw) {
						mob.setYaw(mob.getHeadYaw());
					}
				} else {
					entity.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target);
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

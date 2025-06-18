package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
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

public class Attack implements Transition, TransitionConfig {

	public static final MapCodec<Attack> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(a -> a.entity),
					EntityRefRegistry.CODEC.fieldOf("target").forGetter(a -> a.target)
			).apply(instance, Attack::new)
	);

	private final EntityRef entity;
	private final EntityRef target;

	public Attack(EntityRef entity, EntityRef target) {
		this.entity = entity;
		this.target = target;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		this.entity.get(cutscene.getRefContext()).forEach(entity -> {
			this.target.get(cutscene.createRefContext(entity)).findFirst().ifPresent(target -> {
				if (entity instanceof MobEntity mob && target instanceof LivingEntity livingTarget) {
					mob.setTarget(livingTarget);
				}
			});
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ENTITY_ATTACK;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.ENTITY_ATTACK;
	}
}

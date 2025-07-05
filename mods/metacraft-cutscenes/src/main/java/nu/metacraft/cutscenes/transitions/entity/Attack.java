package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfig;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;

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

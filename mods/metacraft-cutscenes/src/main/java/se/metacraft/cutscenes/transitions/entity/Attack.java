package se.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import se.metacraft.cutscenes.util.IntervalMap;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.core.entity_ref.EntityRef;
import se.metacraft.core.registry.EntityRefRegistry;
import se.metacraft.cutscenes.registry.TransitionConfigRegistry;
import se.metacraft.cutscenes.registry.TransitionRegistry;
import se.metacraft.cutscenes.transitions.Transition;
import se.metacraft.cutscenes.transitions.TransitionType;
import se.metacraft.cutscenes.transitions.config.TransitionConfig;
import se.metacraft.cutscenes.transitions.config.TransitionConfigType;

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
				if (entity instanceof Mob mob && target instanceof LivingEntity livingTarget) {
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

package se.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.core.entity_ref.EntityRef;
import se.metacraft.core.registry.EntityRefRegistry;
import se.metacraft.cutscenes.registry.TransitionConfigRegistry;
import se.metacraft.cutscenes.registry.TransitionRegistry;
import se.metacraft.cutscenes.transitions.Transition;
import se.metacraft.cutscenes.transitions.TransitionType;
import se.metacraft.cutscenes.transitions.config.TransitionConfig;
import se.metacraft.cutscenes.transitions.config.TransitionConfigType;
import se.metacraft.cutscenes.util.IntervalMap;

public class Sneak implements Transition, TransitionConfig {

	public static final MapCodec<Sneak> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity)
			).apply(instance, Sneak::new)
	);

	private final EntityRef entity;

	public Sneak(EntityRef entity) {
		this.entity = entity;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).forEach(entity -> {
			entity.setShiftKeyDown(true);
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).forEach(entity -> {
			entity.setShiftKeyDown(false);
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SNEAK;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SNEAK;
	}
}

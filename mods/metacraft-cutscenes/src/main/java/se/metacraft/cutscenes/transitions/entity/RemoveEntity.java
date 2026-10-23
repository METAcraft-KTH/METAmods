package se.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.metacraft.cutscenes.cutscene.CutsceneInstance;
import se.metacraft.cutscenes.registry.TransitionConfigRegistry;
import se.metacraft.cutscenes.registry.TransitionRegistry;
import se.metacraft.cutscenes.transitions.InstantTransition;
import se.metacraft.cutscenes.transitions.Transition;
import se.metacraft.cutscenes.transitions.TransitionType;
import se.metacraft.cutscenes.transitions.config.TransitionConfigType;
import se.metacraft.cutscenes.util.IntervalMap;
import se.metacraft.core.entity_ref.EntityRef;
import se.metacraft.core.registry.EntityRefRegistry;

public class RemoveEntity extends InstantTransition {

	public static final MapCodec<RemoveEntity> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(a -> a.entity),
					Codec.BOOL.optionalFieldOf("kill", false).forGetter(a -> a.kill)
			).apply(instance, RemoveEntity::new)
	);

	private final EntityRef entity;
	private final boolean kill;

	public RemoveEntity(EntityRef entity, boolean kill) {
		this.entity = entity;
		this.kill = kill;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		this.entity.get(cutscene.getRefContext()).forEach(entity -> {
			if (kill) {
				entity.kill(cutscene.getCutsceneWorld());
			} else {
				entity.discard();
			}
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.ENTITY_REMOVE;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.ENTITY_REMOVE;
	}
}

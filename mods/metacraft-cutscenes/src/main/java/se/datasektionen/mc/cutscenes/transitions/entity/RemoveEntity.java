package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.InstantTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;

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

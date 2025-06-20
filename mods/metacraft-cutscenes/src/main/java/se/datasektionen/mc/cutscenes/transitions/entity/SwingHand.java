package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.InstantTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

public class SwingHand extends InstantTransition {

	public static final MapCodec<SwingHand> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
					ExtraCodecs.HAND_CODEC.optionalFieldOf("hand", Hand.MAIN_HAND).forGetter(t -> t.hand)
			).apply(instance, SwingHand::new)
	);

	private final EntityRef entity;
	private final Hand hand;

	public SwingHand(EntityRef entity, Hand hand) {
		this.entity = entity;
		this.hand = hand;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(cutscene.getRefContext()).filter(
				entity -> entity instanceof LivingEntity
		).map(entity -> (LivingEntity) entity).forEach(entity -> {
			entity.swingHand(hand);
		});
	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SWING_HAND;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SWING_HAND;
	}
}

package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.InstantTransition;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;
import nu.metacraft.lib.util.ExtraCodecs;

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
